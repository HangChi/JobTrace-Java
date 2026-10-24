package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.PasswordResetDelivery;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Sends through the same configured mail webhook used by the existing service. */
@Component
public final class HttpPasswordResetDelivery implements PasswordResetDelivery {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String secret;

    @Autowired
    public HttpPasswordResetDelivery(ObjectMapper mapper,
            @Value("${jobtrace.email-binding.delivery-url:}") String endpoint,
            @Value("${jobtrace.email-binding.delivery-secret:}") String secret) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), mapper, endpoint, secret);
    }

    HttpPasswordResetDelivery(HttpClient client, ObjectMapper mapper,
            String endpoint, String secret) {
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.secret = secret;
    }

    @Override
    public void deliver(String email, String resetUrl) {
        URI uri;
        try {
            uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new DeliveryUnavailableException();
        }
        String body = mapper.writeValueAsString(Map.of("to", email,
                "template", "password_reset", "resetUrl", resetUrl,
                "expiresInSeconds", 3600));
        var builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                .header("content-type", "application/json");
        if (secret != null && !secret.isBlank()) {
            builder.header("authorization", "Bearer " + secret);
        }
        try {
            var response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryUnavailableException();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeliveryUnavailableException();
        } catch (IOException exception) {
            throw new DeliveryUnavailableException();
        }
    }

    public static final class DeliveryUnavailableException extends RuntimeException {}
}
