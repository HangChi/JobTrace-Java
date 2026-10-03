package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.admin.application.AdminAccessCommand;
import com.jobtrace.admin.domain.AdminAccessAction;
import com.jobtrace.admin.domain.AdminAccessChange;
import com.jobtrace.admin.domain.AdminAccessResult;
import com.jobtrace.admin.domain.AdminUserNotFoundException;
import com.jobtrace.admin.domain.CurrentAdminRequiredException;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@SpringBootTest
class AdminAccessCommandIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AdminAccessCommand command;

    @BeforeEach
    void seed() {
        jdbc.execute("drop table if exists admin_audit_events, interview_reviews, "
                + "applications, sessions, users cascade");
        var populator = new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/admin-access-change.sql"));
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.execute(dataSource);
        jdbc.update("""
                insert into users(id,username,display_username,email,role,disabled,access_version)
                values ('admin-a','admin-a','Admin A','admin-a@example.test','admin',false,1),
                  ('admin-b','admin-b','Admin B','admin-b@example.test','admin',false,1),
                  ('user-a','alice','Alice','alice@example.test','user',false,2),
                  ('user-disabled','disabled','Disabled','disabled@example.test','user',true,5)
                """);
        jdbc.update("insert into sessions(id,user_id) values ('session-a','user-a')");
    }

    @Test
    void performsAllFourActionsAndRevokesOnlyDisabledSessions() {
        var promoted = execute("user-a", 2, AdminAccessAction.PROMOTE_ADMIN);
        assertState(promoted, "admin", false, 3);

        var demoted = execute("user-a", 3, AdminAccessAction.DEMOTE_ADMIN);
        assertState(demoted, "user", false, 4);

        var disabled = execute("user-a", 4, AdminAccessAction.DISABLE_USER);
        assertState(disabled, "user", true, 5);
        assertThat(count("sessions")).isZero();

        var enabled = execute("user-a", 5, AdminAccessAction.ENABLE_USER);
        assertState(enabled, "user", false, 6);
        assertThat(count("sessions")).isZero();
        assertThat(count("admin_audit_events")).isEqualTo(4);
    }

    @Test
    void identicalRetryReplaysAndChangedPayloadConflicts() {
        UUID requestId = UUID.randomUUID();
        AdminAccessChange first = change(requestId, 2, AdminAccessAction.PROMOTE_ADMIN);
        AdminAccessResult original = command.execute("admin-a", "user-a", first);
        AdminAccessResult replay = command.execute("admin-a", "user-a", first);
        AdminAccessResult conflict = command.execute("admin-a", "user-a",
                change(requestId, 2, AdminAccessAction.DISABLE_USER));

        assertThat(original.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.auditEventId()).isEqualTo(original.auditEventId());
        assertThat(conflict.failureCode()).isEqualTo("idempotency_conflict");
        assertThat(count("admin_audit_events")).isOne();
        assertThat(version("user-a")).isEqualTo(3);
    }

    @Test
    void recordsStaleSelfAndLastAdministratorDenialsWithoutChangingState() {
        AdminAccessResult stale = command.execute("admin-a", "user-a",
                change(UUID.randomUUID(), 1, AdminAccessAction.DISABLE_USER));
        assertThat(stale.failureCode()).isEqualTo("access_version_conflict");
        assertThat(version("user-a")).isEqualTo(2);

        AdminAccessResult self = command.execute("admin-a", "admin-a",
                change(UUID.randomUUID(), 1, AdminAccessAction.DEMOTE_ADMIN));
        assertThat(self.failureCode()).isEqualTo("self_confirmation_required");

        jdbc.update("update users set disabled=true where id='admin-b'");
        AdminAccessResult last = command.execute("admin-a", "admin-a",
                new AdminAccessChange(UUID.randomUUID(), 1,
                        AdminAccessAction.DISABLE_USER, "Confirmed administrator action", true));
        assertThat(last.failureCode()).isEqualTo("last_admin");
        assertThat(jdbc.queryForObject(
                "select disabled from users where id='admin-a'", Boolean.class)).isFalse();
        assertThat(count("admin_audit_events")).isEqualTo(3);
    }

    @Test
    void rejectsMissingTargetAndNoLongerCurrentAdministrator() {
        assertThatThrownBy(() -> command.execute("admin-a", "missing",
                change(UUID.randomUUID(), 1, AdminAccessAction.DISABLE_USER)))
                .isInstanceOf(AdminUserNotFoundException.class);
        jdbc.update("update users set disabled=true where id='admin-a'");
        assertThatThrownBy(() -> command.execute("admin-a", "user-a",
                change(UUID.randomUUID(), 2, AdminAccessAction.DISABLE_USER)))
                .isInstanceOf(CurrentAdminRequiredException.class);
        assertThat(count("admin_audit_events")).isZero();
    }

    @Test
    void concurrentIdenticalCommandsConvergeOnOneDurableResult() throws Exception {
        UUID requestId = UUID.randomUUID();
        AdminAccessChange change = change(requestId, 2, AdminAccessAction.PROMOTE_ADMIN);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return command.execute("admin-a", "user-a", change);
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return command.execute("admin-a", "user-a", change);
            });
            start.countDown();
            var results = java.util.List.of(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS));
            assertThat(results).extracting(AdminAccessResult::auditEventId)
                    .containsOnly(results.getFirst().auditEventId());
            assertThat(results).extracting(AdminAccessResult::replayed)
                    .containsExactlyInAnyOrder(false, true);
        }
        assertThat(version("user-a")).isEqualTo(3);
        assertThat(count("admin_audit_events")).isOne();
    }

    @Test
    void replayPathMeetsOneSecondP95Budget() {
        UUID requestId = UUID.randomUUID();
        AdminAccessChange change = change(requestId, 2, AdminAccessAction.PROMOTE_ADMIN);
        command.execute("admin-a", "user-a", change);
        for (int index = 0; index < 5; index++) {
            command.execute("admin-a", "user-a", change);
        }
        long[] timings = new long[40];
        for (int index = 0; index < timings.length; index++) {
            long started = System.nanoTime();
            command.execute("admin-a", "user-a", change);
            timings[index] = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        }
        java.util.Arrays.sort(timings);
        long p95 = timings[(int) Math.ceil(timings.length * 0.95) - 1];
        System.out.println("010 admin access replay p95 ms: " + p95);
        assertThat(p95).isLessThanOrEqualTo(1_000);
        assertThat(count("admin_audit_events")).isOne();
    }

    private AdminAccessResult execute(
            String target, long version, AdminAccessAction action) {
        return command.execute("admin-a", target, change(UUID.randomUUID(), version, action));
    }

    private static AdminAccessChange change(
            UUID requestId, long version, AdminAccessAction action) {
        return new AdminAccessChange(requestId, version, action,
                "Confirmed administrator action", false);
    }

    private static void assertState(
            AdminAccessResult result, String role, boolean disabled, long version) {
        assertThat(result.outcome()).isEqualTo(AdminAccessResult.Outcome.SUCCEEDED);
        assertThat(result.role()).isEqualTo(role);
        assertThat(result.disabled()).isEqualTo(disabled);
        assertThat(result.accessVersion()).isEqualTo(version);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private long version(String userId) {
        return jdbc.queryForObject(
                "select access_version from users where id=?", Long.class, userId);
    }
}
