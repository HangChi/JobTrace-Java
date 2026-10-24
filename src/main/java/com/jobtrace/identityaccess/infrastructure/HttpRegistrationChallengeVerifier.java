package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.RegistrationChallengeVerifier;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException.Reason;
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

/** Optional five-second challenge boundary; never logs tokens or verifier secrets. */
@Component
public final class HttpRegistrationChallengeVerifier implements RegistrationChallengeVerifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String secret;

    @Autowired
    public HttpRegistrationChallengeVerifier(ObjectMapper mapper,
            @Value("${jobtrace.registration.challenge-url:}") String endpoint,
            @Value("${jobtrace.registration.challenge-secret:}") String secret) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), mapper, endpoint, secret);
    }

    HttpRegistrationChallengeVerifier(HttpClient client, ObjectMapper mapper,
            String endpoint, String secret) {
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.secret = secret;
    }

    @Override
    public void verify(String token) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new RegistrationChallengeException(Reason.REQUIRED);
        }
        URI uri = endpointUri();
        if (secret == null || secret.isBlank()) {
            throw new RegistrationChallengeException(Reason.UNAVAILABLE);
        }
        String body = mapper.writeValueAsString(Map.of("token", token, "secret", secret));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !success(response.body())) {
                throw new RegistrationChallengeException(Reason.FAILED);
            }
        } catch (IOException exception) {
            throw new RegistrationChallengeException(Reason.UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistrationChallengeException(Reason.UNAVAILABLE);
        }
    }

    private URI endpointUri() {
        try {
            URI uri = URI.create(endpoint);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null) {
                return uri;
            }
        } catch (IllegalArgumentException exception) {
            // Configuration errors are handled as an unavailable challenge boundary.
        }
        throw new RegistrationChallengeException(Reason.UNAVAILABLE);
    }

    private boolean success(String body) {
        try {
            var json = mapper.readTree(body);
            return json != null && json.has("success") && json.get("success").booleanValue();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
