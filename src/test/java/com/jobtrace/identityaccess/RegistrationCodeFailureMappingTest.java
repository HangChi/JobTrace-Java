package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.jobtrace.identityaccess.application.RequestRegistrationEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.domain.RegistrationEmailConflictException;
import com.jobtrace.identityaccess.web.RegistrationCodeOriginGuard;
import com.jobtrace.identityaccess.web.RegistrationEmailCodeController;
import com.jobtrace.identityaccess.web.RegistrationEmailCodeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class RegistrationCodeFailureMappingTest {

    private final RequestRegistrationEmailCode useCase = mock(RequestRegistrationEmailCode.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RegistrationEmailCodeController controller = new RegistrationEmailCodeController(
            useCase, new RegistrationCodeOriginGuard("https://jobtrace.test"),
            new RegistrationEmailCodeMetrics(registry));

    @Test
    void recordsOnlyFixedFailureLabels() {
        var email = new ObjectMapper().readTree("\"fresh@example.test\"");
        Map<String, tools.jackson.databind.JsonNode> body = Map.of("email", email);
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://jobtrace.test");
        Map<String, RuntimeException> failures = Map.ofEntries(
                Map.entry("invalid", new AccountEmailCodeValidationException("email")),
                Map.entry("conflict", new RegistrationEmailConflictException()),
                Map.entry("rate_limited", new AccountEmailCodeRateLimitedException()),
                Map.entry("invalid_recipient", new AccountEmailCodeDeliveryException(false, true)),
                Map.entry("unavailable", new AccountEmailCodeDeliveryException(true, false)),
                Map.entry("delivery_failure", new AccountEmailCodeDeliveryException(false, false)),
                Map.entry("dependency_failure", new DataAccessResourceFailureException("db")),
                Map.entry("failure", new IllegalStateException("unexpected")));
        for (var entry : failures.entrySet()) {
            reset(useCase);
            doThrow(entry.getValue()).when(useCase).execute("fresh@example.test");
            assertThatThrownBy(() -> controller.request(body, request))
                    .isSameAs(entry.getValue());
            assertThat(registry.get("jobtrace.identity_access.registration.code.requests")
                    .tag("outcome", entry.getKey()).counter().count()).isEqualTo(1);
        }
        reset(useCase);
        doThrow(new AccountEmailBindingUnavailableException())
                .when(useCase).execute("fresh@example.test");
        assertThatThrownBy(() -> controller.request(body, request))
                .isInstanceOf(AccountEmailBindingUnavailableException.class);
        assertThat(registry.get("jobtrace.identity_access.registration.code.requests")
                .tag("outcome", "unavailable").counter().count()).isEqualTo(2);
        assertThatThrownBy(() -> controller.request(null, request))
                .isInstanceOf(AccountEmailCodeValidationException.class);
        var forbidden = new MockHttpServletRequest();
        forbidden.addHeader("Origin", "https://other.test");
        assertThatThrownBy(() -> controller.request(body, forbidden))
                .isInstanceOf(RegistrationCodeOriginException.class);
        assertThat(registry.get("jobtrace.identity_access.registration.code.requests")
                .tag("outcome", "csrf_rejected").counter().count()).isEqualTo(1);
    }

    @Test
    void mapsSafeProblemResponses() {
        var request = new MockHttpServletRequest();
        assertThat(controller.origin(new RegistrationCodeOriginException(), request)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(controller.validation(new AccountEmailCodeValidationException("email"), request)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(controller.conflict(new RegistrationEmailConflictException(), request)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(controller.rateLimited(new AccountEmailCodeRateLimitedException(), request)
                .getStatusCode().value()).isEqualTo(429);
        assertThat(controller.deliveryFailure(
                new AccountEmailCodeDeliveryException(false, true), request)
                .getStatusCode().value()).isEqualTo(422);
        assertThat(controller.deliveryFailure(
                new AccountEmailCodeDeliveryException(true, false), request)
                .getStatusCode().value()).isEqualTo(503);
        assertThat(controller.deliveryFailure(
                new AccountEmailCodeDeliveryException(false, false), request)
                .getStatusCode().value()).isEqualTo(502);
        assertThat(controller.signingUnavailable(
                new AccountEmailBindingUnavailableException(), request)
                .getStatusCode().value()).isEqualTo(503);
    }
}
