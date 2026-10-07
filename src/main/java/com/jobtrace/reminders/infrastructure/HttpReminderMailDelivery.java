package com.jobtrace.reminders.infrastructure;

import com.jobtrace.reminders.application.ReminderDeliveryStore.Claim;
import com.jobtrace.reminders.application.ReminderMailDelivery;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Uses the existing mail webhook; no recipient or task text is logged. */
@Component
public class HttpReminderMailDelivery implements ReminderMailDelivery {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter BEIJING = DateTimeFormatter
            .ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String secret;
    private final String baseUrl;

    @Autowired
    public HttpReminderMailDelivery(ObjectMapper mapper,
            @Value("${jobtrace.email-binding.delivery-url:}") String endpoint,
            @Value("${jobtrace.email-binding.delivery-secret:}") String secret,
            @Value("${jobtrace.reminders.base-url:http://localhost:3000}") String baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(),
                mapper, endpoint, secret, baseUrl);
    }

    HttpReminderMailDelivery(HttpClient client, ObjectMapper mapper,
            String endpoint, String secret, String baseUrl) {
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.secret = secret;
        this.baseUrl = baseUrl;
    }

    @Override
    public void deliver(Claim claim) {
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
        String body = mapper.writeValueAsString(Map.of(
                "to", claim.recipient(),
                "template", "scheduled_reminder",
                "companyName", claim.companyName(),
                "positionName", claim.positionName(),
                "title", claim.title(),
                "eventAt", BEIJING.format(claim.eventAt()),
                "applicationUrl", baseUrl.replaceAll("/$", "")
                        + "/applications/" + claim.applicationId()));
        var request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                .header("content-type", "application/json");
        if (secret != null && !secret.isBlank()) {
            request.header("authorization", "Bearer " + secret);
        }
        try {
            var response = client.send(request.POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
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
