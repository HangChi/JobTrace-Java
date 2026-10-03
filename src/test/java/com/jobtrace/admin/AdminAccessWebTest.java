package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobtrace.admin.application.AdminAccessCommand;
import com.jobtrace.admin.application.AdminReadQuery;
import com.jobtrace.admin.application.ChangeManagedUserAccess;
import com.jobtrace.admin.application.GetAdminSummary;
import com.jobtrace.admin.application.GetManagedUserDetail;
import com.jobtrace.admin.application.ListAdminAuditEvents;
import com.jobtrace.admin.application.ListManagedUsers;
import com.jobtrace.admin.domain.AdminAccessResult;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.web.AdminAccessRequest;
import com.jobtrace.admin.web.AdminReadController;
import com.jobtrace.admin.web.AdminReadMetrics;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.shared.web.RequestIdFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AdminAccessWebTest {

    @Test
    void administratorReceivesPrivateSuccessfulResponseAndBoundedMetric() {
        AdminAccessCommand command = mock(AdminAccessCommand.class);
        AdminReadQuery query = mock(AdminReadQuery.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(command.execute(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AdminAccessResult(AdminAccessResult.Outcome.SUCCEEDED,
                        null, "audit-a", false, "user-a", "user", true, 3L));
        when(query.user("user-a")).thenReturn(Optional.of(user()));

        var response = controller(command, query, registry).changeUserAccess(
                "user-a", request(), principal(BridgeIdentity.Role.ADMIN));

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getBody().auditEventId()).isEqualTo("audit-a");
        assertThat(registry.counter("jobtrace.admin.access_change.requests",
                "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void ordinaryUserCannotInvokeCommand() {
        var controller = controller(mock(AdminAccessCommand.class),
                mock(AdminReadQuery.class), new SimpleMeterRegistry());
        assertThatThrownBy(() -> controller.changeUserAccess(
                "user-a", request(), principal(BridgeIdentity.Role.USER)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void conflictProblemIncludesSafeRecoveryStateAndRequestId() {
        AdminAccessCommand command = mock(AdminAccessCommand.class);
        AdminReadQuery query = mock(AdminReadQuery.class);
        when(command.execute(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AdminAccessResult(AdminAccessResult.Outcome.CONFLICT,
                        "access_version_conflict", "audit-a", false,
                        "user-a", "user", false, 7L));
        var controller = controller(command, query, new SimpleMeterRegistry());
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-http");

        try {
            controller.changeUserAccess(
                    "user-a", request(), principal(BridgeIdentity.Role.ADMIN));
            throw new AssertionError("expected conflict");
        } catch (com.jobtrace.admin.domain.AdminAccessConflictException conflict) {
            var response = controller.accessConflict(conflict, servletRequest);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
            assertThat(response.getBody().getProperties())
                    .containsEntry("requestId", "request-http")
                    .containsEntry("auditEventId", "audit-a")
                    .containsKey("latestAccessState");
        }
    }

    private static AdminReadController controller(
            AdminAccessCommand command,
            AdminReadQuery query,
            SimpleMeterRegistry registry) {
        return new AdminReadController(
                new GetAdminSummary(query, Clock.systemUTC()),
                new ListManagedUsers(query),
                new GetManagedUserDetail(query),
                new ListAdminAuditEvents(query),
                new ChangeManagedUserAccess(command, query),
                new AdminReadMetrics(registry));
    }

    private static AdminAccessRequest request() {
        return new AdminAccessRequest("0199a55c-9b00-7000-8000-000000000010",
                2L, "disable_user", "Confirmed security incident", false);
    }

    private static AdminUser.Summary user() {
        return new AdminUser.Summary("user-a", "Alice", "alice@example.test",
                "alice@internal.test", "user", true, 3,
                "2026-01-01T00:00:00.000Z", null, 0, 0);
    }

    private static UsernamePasswordAuthenticationToken principal(BridgeIdentity.Role role) {
        var principal = new UsernamePasswordAuthenticationToken("admin-a", null, List.of());
        principal.setDetails(new BridgeIdentity("admin-a", role, 1));
        return principal;
    }
}
