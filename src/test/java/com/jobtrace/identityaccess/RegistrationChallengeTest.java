package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;

import com.jobtrace.identityaccess.application.RegistrationAttemptLimiter;
import com.jobtrace.identityaccess.application.RegistrationChallengeVerifier;
import com.jobtrace.identityaccess.application.RegistrationPreflight;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException.Reason;
import com.jobtrace.identityaccess.infrastructure.HttpRegistrationChallengeVerifier;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class RegistrationChallengeTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private int status = 200;
    private String response = "{\"success\":true}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsChallengeTokenAndConfiguredSecret() {
        verifier("challenge-secret").verify("token-123");
        var json = new ObjectMapper().readTree(body.get());
        assertThat(json.get("token").textValue()).isEqualTo("token-123");
        assertThat(json.get("secret").textValue()).isEqualTo("challenge-secret");
    }

    @Test
    void disabledChallengeAllowsMissingToken() {
        new HttpRegistrationChallengeVerifier(new ObjectMapper(), "", "")
                .verify(null);
        assertThat(body.get()).isNull();
    }

    @Test
    void requiredRejectedAndUnavailableChallengesFailClosed() {
        assertReason(() -> verifier("secret").verify(null), Reason.REQUIRED);
        assertReason(() -> verifier("secret").verify("  "), Reason.REQUIRED);
        assertReason(() -> verifier("").verify("token"), Reason.UNAVAILABLE);
        for (String endpoint : new String[] {"not a URI", "file:///tmp/challenge"}) {
            assertReason(() -> new HttpRegistrationChallengeVerifier(
                    new ObjectMapper(), endpoint, "secret").verify("token"),
                    Reason.UNAVAILABLE);
        }
        response = "{\"success\":false}";
        assertReason(() -> verifier("secret").verify("token"), Reason.FAILED);
        status = 500;
        response = "{\"success\":true}";
        assertReason(() -> verifier("secret").verify("token"), Reason.FAILED);
        status = 200;
        response = "not-json";
        assertReason(() -> verifier("secret").verify("token"), Reason.FAILED);
        response = "{}";
        assertReason(() -> verifier("secret").verify("token"), Reason.FAILED);
    }

    @Test
    void registrationLimitPrecedesChallenge() {
        RegistrationAttemptLimiter limiter = Mockito.mock(RegistrationAttemptLimiter.class);
        RegistrationChallengeVerifier challenge = Mockito.mock(RegistrationChallengeVerifier.class);
        new RegistrationPreflight(limiter, challenge).check("token");
        var ordered = inOrder(limiter, challenge);
        ordered.verify(limiter).consumeRegistrationAttempt();
        ordered.verify(challenge).verify("token");
    }

    private HttpRegistrationChallengeVerifier verifier(String secret) {
        return new HttpRegistrationChallengeVerifier(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/verify", secret);
    }

    private void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            Reason reason) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(RegistrationChallengeException.class,
                        error -> assertThat(error.reason()).isEqualTo(reason));
    }
}
