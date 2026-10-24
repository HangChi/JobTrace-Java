package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.jobtrace.identityaccess.application.RequestAccountEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.web.AccountEmailCodeController;
import com.jobtrace.identityaccess.web.AccountEmailCodeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

class AccountEmailCodeFailureMappingTest {

    private final RequestAccountEmailCode useCase = mock(RequestAccountEmailCode.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AccountEmailCodeController controller = new AccountEmailCodeController(
            useCase, new AccountEmailCodeMetrics(registry));

    @Test
    void recordsOnlyFixedFailureLabels() {
        var principal = new UsernamePasswordAuthenticationToken("owner", null);
        principal.setDetails(new BridgeIdentity("owner", BridgeIdentity.Role.USER, 0));
        var email = new ObjectMapper().readTree("\"fresh@example.test\"");
        Map<String, tools.jackson.databind.JsonNode> body = Map.of("email", email);
        Map<String, RuntimeException> failures = Map.ofEntries(
                Map.entry("invalid", new AccountEmailCodeValidationException("email")),
                Map.entry("conflict", new AccountEmailConflictException()),
                Map.entry("rate_limited", new AccountEmailCodeRateLimitedException()),
                Map.entry("invalid_recipient", new AccountEmailCodeDeliveryException(false, true)),
                Map.entry("unavailable", new AccountEmailCodeDeliveryException(true, false)),
                Map.entry("delivery_failure", new AccountEmailCodeDeliveryException(false, false)),
                Map.entry("invalid_state", new AccountEmailCodeStateException()),
                Map.entry("dependency_failure", new DataAccessResourceFailureException("db")),
                Map.entry("failure", new IllegalStateException("unexpected")));
        for (var entry : failures.entrySet()) {
            reset(useCase);
            doThrow(entry.getValue()).when(useCase)
                    .execute("owner", "session", "fresh@example.test");
            assertThatThrownBy(() -> controller.request("session", body, principal))
                    .isSameAs(entry.getValue());
            assertThat(registry.get("jobtrace.identity_access.email.code.requests")
                    .tag("outcome", entry.getKey()).counter().count()).isEqualTo(1);
        }
        assertThatThrownBy(() -> controller.request("session", body, null))
                .isInstanceOf(com.jobtrace.shared.web.Problem.class);
        assertThat(registry.get("jobtrace.identity_access.email.code.requests")
                .tag("outcome", "denied_identity").counter().count()).isEqualTo(1);
        assertThatThrownBy(() -> controller.request("session", null, principal))
                .isInstanceOf(AccountEmailCodeValidationException.class);
    }

    @Test
    void mapsDeliveryAndSigningProblemsWithoutSensitiveDetails() {
        var request = new MockHttpServletRequest();
        assertThat(controller.deliveryFailure(
                new AccountEmailCodeDeliveryException(false, true), request)
                .getStatusCode().value()).isEqualTo(422);
        assertThat(controller.deliveryFailure(
                new AccountEmailCodeDeliveryException(true, false), request)
                .getStatusCode().value()).isEqualTo(503);
        assertThat(controller.deliveryFailure(
                new AccountEmailCodeDeliveryException(false, false), request)
                .getStatusCode().value()).isEqualTo(502);
        assertThat(controller.missingSigningSecret(
                new AccountEmailBindingUnavailableException(), request)
                .getStatusCode().value()).isEqualTo(503);
        assertThat(controller.validation(
                new AccountEmailCodeValidationException("email"), request)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(controller.conflict(new AccountEmailConflictException(), request)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(controller.rateLimited(new AccountEmailCodeRateLimitedException(), request)
                .getStatusCode().value()).isEqualTo(429);
        assertThat(controller.invalidState(new AccountEmailCodeStateException(), request)
                .getStatusCode().value()).isEqualTo(500);
    }
}
