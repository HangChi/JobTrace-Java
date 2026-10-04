package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobtrace.admin.application.AdminReadQuery;
import com.jobtrace.admin.application.GetAdminSummary;
import com.jobtrace.admin.application.GetManagedUserDetail;
import com.jobtrace.admin.application.ListAdminAuditEvents;
import com.jobtrace.admin.application.ListManagedUsers;
import com.jobtrace.admin.domain.AdminAuditEvent;
import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.domain.AdminSummary;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.domain.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminUseCasesTest {

    @Test
    void summarySourcesDegradeIndependently() {
        AdminReadQuery query = mock(AdminReadQuery.class);
        when(query.counts()).thenReturn(new AdminSummary.Counts(3, 2, 1, 1, 4, 5));
        when(query.activity()).thenThrow(new IllegalStateException("unavailable"));
        var useCase = new GetAdminSummary(query,
                Clock.fixed(Instant.parse("2026-10-03T01:02:03Z"), ZoneOffset.UTC));

        AdminSummary result = useCase.execute();
        assertThat(result.generatedAt()).isEqualTo("2026-10-03T01:02:03Z");
        assertThat(result.counts().status()).isEqualTo("available");
        assertThat(result.counts().value().users()).isEqualTo(3);
        assertThat(result.activity().status()).isEqualTo("unavailable");
    }

    @Test
    void listUseCasesClampNonEmptyOutOfRangePages() {
        AdminReadQuery query = mock(AdminReadQuery.class);
        AdminQuery.Users users = new AdminQuery.Users(null, AdminQuery.UserRole.ALL,
                AdminQuery.UserStatus.ALL, null, null, 9, 2);
        PageResult<AdminUser.Summary> empty = PageResult.of(List.of(), 3, 9, 2);
        PageResult<AdminUser.Summary> last = PageResult.of(List.of(user()), 3, 2, 2);
        when(query.users(users)).thenReturn(empty);
        when(query.users(new AdminQuery.Users(null, AdminQuery.UserRole.ALL,
                AdminQuery.UserStatus.ALL, null, null, 2, 2))).thenReturn(last);
        assertThat(new ListManagedUsers(query).execute(users)).isSameAs(last);

        AdminQuery.Audit audit = new AdminQuery.Audit(null, null, AdminQuery.EventType.ALL,
                AdminQuery.Outcome.ALL, null, null, 5, 1);
        PageResult<AdminAuditEvent> auditEmpty = PageResult.of(List.of(), 2, 5, 1);
        PageResult<AdminAuditEvent> auditLast = PageResult.of(List.of(event()), 2, 2, 1);
        when(query.audit(audit)).thenReturn(auditEmpty);
        when(query.audit(new AdminQuery.Audit(null, null, AdminQuery.EventType.ALL,
                AdminQuery.Outcome.ALL, null, null, 2, 1))).thenReturn(auditLast);
        assertThat(new ListAdminAuditEvents(query).execute(audit)).isSameAs(auditLast);
    }

    @Test
    void detailComposesBoundedContentAndClampsEachPage() {
        AdminReadQuery query = mock(AdminReadQuery.class);
        when(query.user("user-a")).thenReturn(Optional.of(user()));
        when(query.recentAudit("user-a", 10)).thenReturn(List.of(event()));
        when(query.applications("user-a", 8)).thenReturn(PageResult.of(List.of(), 1, 8, 10));
        when(query.applications("user-a", 1)).thenReturn(PageResult.of(List.of(), 1, 1, 10));
        when(query.interviews("user-a", 9)).thenReturn(PageResult.of(List.of(), 0, 9, 10));

        AdminUser.Detail detail = new GetManagedUserDetail(query)
                .execute("user-a", new AdminQuery.Content(8, 9));
        assertThat(detail.id()).isEqualTo("user-a");
        assertThat(detail.recentAuditEvents()).hasSize(1);
        assertThat(detail.applications().page()).isEqualTo(1);
        assertThat(detail.interviews().page()).isEqualTo(9);
    }

    private static AdminUser.Summary user() {
        return new AdminUser.Summary("user-a", "Alice", "alice@example.test",
                "alice@internal.test", "user", false, 2,
                "2026-09-01T00:00:00.000Z", null, 1, 1);
    }

    private static AdminAuditEvent event() {
        var before = new AdminAuditEvent.AccessState("user", false, 1);
        return new AdminAuditEvent("event-a", "request-a", "admin-a", "Admin", false,
                "user-a", "Alice", false, "disable_user", "succeeded", "reason",
                before, new AdminAuditEvent.AccessState("user", true, 2), null,
                "2026-09-02T00:00:00.000Z");
    }
}
