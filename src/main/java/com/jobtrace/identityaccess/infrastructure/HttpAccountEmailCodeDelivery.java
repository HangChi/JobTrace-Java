package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Calls the existing email-delivery contract without logging codes or addresses. */
@Component
public final class HttpAccountEmailCodeDelivery implements AccountEmailCodeDelivery {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String secret;

    @Autowired
    public HttpAccountEmailCodeDelivery(ObjectMapper mapper,
            @Value("${jobtrace.email-binding.delivery-url:}") String endpoint,
            @Value("${jobtrace.email-binding.delivery-secret:}") String secret) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), mapper, endpoint, secret);
    }

    HttpAccountEmailCodeDelivery(HttpClient client, ObjectMapper mapper,
            String endpoint, String secret) {
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.secret = secret;
    }

    @Override
    public void ensureAvailable() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new AccountEmailCodeDeliveryException(true, false);
        }
        try {
            URI uri = URI.create(endpoint);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new AccountEmailCodeDeliveryException(true, false);
            }
        } catch (IllegalArgumentException exception) {
            throw new AccountEmailCodeDeliveryException(true, false);
        }
    }

    @Override
    public void deliver(String email, String code) {
        ensureAvailable();
        String body = mapper.writeValueAsString(Map.of(
                "to", email,
                "template", "email_verification_code",
                "code", code,
                "expiresInSeconds", 600));
        var request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("content-type", "application/json");
        if (secret != null && !secret.isBlank()) {
            request.header("authorization", "Bearer " + secret);
        }
        try {
            HttpResponse<String> response = client.send(
                    request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return;
            }
            if (response.statusCode() == 422 && invalidRecipient(response.body())) {
                throw new AccountEmailCodeDeliveryException(false, true);
            }
            throw new AccountEmailCodeDeliveryException(false, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AccountEmailCodeDeliveryException(false, false);
        } catch (IOException exception) {
            throw new AccountEmailCodeDeliveryException(false, false);
        }
    }

    private boolean invalidRecipient(String body) {
        try {
            var json = mapper.readTree(body);
            return json != null && json.has("error")
                    && "invalid_recipient".equals(json.get("error").textValue());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
