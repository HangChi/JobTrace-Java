package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.RevokeAccountSession;
import com.jobtrace.identityaccess.domain.AccountSessionNotFoundException;
import com.jobtrace.identityaccess.infrastructure.PostgresAccountSessionCommand;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountSessionRevocationIntegrationTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private RevokeAccountSession revokeSession;

    @Autowired
    private PostgresAccountSessionCommand command;

    @Test
    void deletesOwnedCurrentAndExpiredRowsButNeverForeignOrCaseVariants() {
        assertThat(revokeSession.execute(OWNER, CURRENT).revoked()).isTrue();
        assertThat(revokeSession.execute(OWNER, "session-expired").revoked()).isTrue();
        assertThatThrownBy(() -> revokeSession.execute(OWNER, "session-foreign"))
                .isInstanceOf(AccountSessionNotFoundException.class);
        assertThatThrownBy(() -> revokeSession.execute(OWNER, "SESSION-OLDER"))
                .isInstanceOf(AccountSessionNotFoundException.class);

        assertThat(sessionIds()).containsExactlyInAnyOrder(
                "session-null-agent", "session-older", "session-foreign");
    }

    @Test
    void repeatedDeletionIsNotFoundAndDoesNotTouchAnotherRow() {
        assertThat(command.revoke(OWNER, "session-older")).isTrue();
        assertThat(command.revoke(OWNER, "session-older")).isFalse();
        assertThat(sessionIds()).contains(CURRENT, "session-null-agent", "session-foreign");
    }

    @Test
    void concurrentAttemptsProduceExactlyOneDeletion() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            return command.revoke(OWNER, "session-older");
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from sessions where id='session-older'", Integer.class))
                .isZero();
    }

    private List<String> sessionIds() {
        return jdbc.queryForList("select id from sessions order by id", String.class);
    }
}
