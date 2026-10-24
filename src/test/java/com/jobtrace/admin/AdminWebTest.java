package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobtrace.admin.application.AdminReadQuery;
import com.jobtrace.admin.application.ChangeManagedUserAccess;
import com.jobtrace.admin.application.GetAdminSummary;
import com.jobtrace.admin.application.GetManagedUserDetail;
import com.jobtrace.admin.application.ListAdminAuditEvents;
import com.jobtrace.admin.application.ListManagedUsers;
import com.jobtrace.admin.domain.AdminSummary;
import com.jobtrace.admin.web.AdminNoStoreFilter;
import com.jobtrace.admin.web.AdminReadController;
import com.jobtrace.admin.web.AdminReadMetrics;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AdminWebTest {

    @Test
    void controllerRequiresAdminAndMarksSuccessfulResponsePrivateNoStore() {
        AdminReadQuery query = mock(AdminReadQuery.class);
        when(query.counts()).thenReturn(new AdminSummary.Counts(1, 1, 0, 1, 0, 0));
        when(query.activity()).thenReturn(new AdminSummary.Activity(
                new AdminSummary.ActivityWindows(0, 0, 0, 0), List.of()));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminReadController controller = controller(query, registry);

        var response = controller.summary(principal(BridgeIdentity.Role.ADMIN));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getBody().counts().status()).isEqualTo("available");
        assertThat(registry.counter("jobtrace.admin.read.requests",
                "operation", "summary", "outcome", "success").count()).isEqualTo(1);

        assertThatThrownBy(() -> controller.summary(principal(BridgeIdentity.Role.USER)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void noStoreFilterCoversAdministrationErrorsAndSuccesses() throws Exception {
        AdminNoStoreFilter filter = new AdminNoStoreFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");

        MockHttpServletResponse publicResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/health/live"),
                publicResponse, new MockFilterChain());
        assertThat(publicResponse.getHeader("Cache-Control")).isNull();
    }

    @Test
    void metricLabelsAreStrictlyBounded() {
        AdminReadMetrics metrics = new AdminReadMetrics(new SimpleMeterRegistry());
        assertThatThrownBy(() -> metrics.record("user-a", "success",
                java.time.Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.record("users", "alice@example.test",
                java.time.Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private static AdminReadController controller(
            AdminReadQuery query, SimpleMeterRegistry registry) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC);
        return new AdminReadController(new GetAdminSummary(query, clock),
                new ListManagedUsers(query), new GetManagedUserDetail(query),
                new ListAdminAuditEvents(query),
                new ChangeManagedUserAccess(mock(com.jobtrace.admin.application.AdminAccessCommand.class),
                        query),
                new AdminReadMetrics(registry));
    }

    private static UsernamePasswordAuthenticationToken principal(BridgeIdentity.Role role) {
        var principal = new UsernamePasswordAuthenticationToken("admin-a", null, List.of());
        principal.setDetails(new BridgeIdentity("admin-a", role, 3));
        return principal;
    }
}
