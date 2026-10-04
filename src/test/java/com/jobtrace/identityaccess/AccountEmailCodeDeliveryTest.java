package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.infrastructure.HttpAccountEmailCodeDelivery;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AccountEmailCodeDeliveryTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private int responseStatus = 200;
    private String responseBody = "{}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/delivery", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, bytes.length);
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
    void sendsLegacyPayloadAndOptionalBearerSecret() {
        delivery("delivery-secret").deliver("new@example.test", "012345");
        var json = new ObjectMapper().readTree(body.get());
        assertThat(json.get("to").textValue()).isEqualTo("new@example.test");
        assertThat(json.get("template").textValue()).isEqualTo("email_verification_code");
        assertThat(json.get("code").textValue()).isEqualTo("012345");
        assertThat(json.get("expiresInSeconds").intValue()).isEqualTo(600);
        assertThat(authorization.get()).isEqualTo("Bearer delivery-secret");
    }

    @Test
    void recognizesInvalidRecipientAndOtherProviderFailure() {
        responseStatus = 422;
        responseBody = "{\"error\":\"invalid_recipient\"}";
        assertThatThrownBy(() -> delivery("").deliver("new@example.test", "012345"))
                .isInstanceOfSatisfying(AccountEmailCodeDeliveryException.class,
                        error -> assertThat(error.invalidRecipient()).isTrue());
        assertThat(authorization.get()).isNull();
        responseStatus = 500;
        responseBody = "{}";
        assertThatThrownBy(() -> delivery("").deliver("new@example.test", "012345"))
                .isInstanceOfSatisfying(AccountEmailCodeDeliveryException.class,
                        error -> assertThat(error.unavailable()).isFalse());
    }

    @Test
    void missingOrUnsafeEndpointFailsClosed() {
        for (String endpoint : new String[] {"", "file:///tmp/mail", "not a URI"}) {
            var delivery = new HttpAccountEmailCodeDelivery(
                    new ObjectMapper(), endpoint, "");
            assertThatThrownBy(delivery::ensureAvailable)
                    .isInstanceOfSatisfying(AccountEmailCodeDeliveryException.class,
                            error -> assertThat(error.unavailable()).isTrue());
        }
    }

    private HttpAccountEmailCodeDelivery delivery(String secret) {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/delivery";
        return new HttpAccountEmailCodeDelivery(new ObjectMapper(), endpoint, secret);
    }
}
