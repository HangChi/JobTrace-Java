package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobtrace.admin.application.AdminAccessCommand;
import com.jobtrace.admin.application.AdminReadQuery;
import com.jobtrace.admin.application.ChangeManagedUserAccess;
import com.jobtrace.admin.domain.AdminAccessAction;
import com.jobtrace.admin.domain.AdminAccessChange;
import com.jobtrace.admin.domain.AdminAccessConflictException;
import com.jobtrace.admin.domain.AdminAccessResult;
import com.jobtrace.admin.domain.AdminUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeManagedUserAccessTest {

    @Test
    void returnsRefreshedUserAfterSuccess() {
        AdminAccessCommand command = mock(AdminAccessCommand.class);
        AdminReadQuery query = mock(AdminReadQuery.class);
        AdminAccessChange change = change();
        when(command.execute("admin-a", "user-a", change)).thenReturn(new AdminAccessResult(
                AdminAccessResult.Outcome.SUCCEEDED, null, "audit-a", false,
                "user-a", "user", true, 3L));
        when(query.user("user-a")).thenReturn(Optional.of(user(true, 3)));

        var response = new ChangeManagedUserAccess(command, query)
                .execute("admin-a", "user-a", change);

        assertThat(response.auditEventId()).isEqualTo("audit-a");
        assertThat(response.user().disabled()).isTrue();
    }

    @Test
    void mapsRecordedFailureToActionableConflict() {
        AdminAccessCommand command = mock(AdminAccessCommand.class);
        AdminReadQuery query = mock(AdminReadQuery.class);
        AdminAccessChange change = change();
        when(command.execute("admin-a", "user-a", change)).thenReturn(new AdminAccessResult(
                AdminAccessResult.Outcome.CONFLICT, "access_version_conflict", "audit-a", false,
                "user-a", "user", false, 4L));

        assertThatThrownBy(() -> new ChangeManagedUserAccess(command, query)
                .execute("admin-a", "user-a", change))
                .isInstanceOfSatisfying(AdminAccessConflictException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("access_version_conflict");
                    assertThat(failure.auditEventId()).isEqualTo("audit-a");
                    assertThat(failure.latestState().accessVersion()).isEqualTo(4);
                });
    }

    private static AdminAccessChange change() {
        return new AdminAccessChange(UUID.randomUUID(), 2,
                AdminAccessAction.DISABLE_USER, "Confirmed security incident", false);
    }

    private static AdminUser.Summary user(boolean disabled, long version) {
        return new AdminUser.Summary("user-a", "Alice", "alice@example.test",
                "alice@internal.test", "user", disabled, version,
                "2026-01-01T00:00:00.000Z", null, 0, 0);
    }
}
