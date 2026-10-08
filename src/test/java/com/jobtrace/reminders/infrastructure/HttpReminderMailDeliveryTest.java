package com.jobtrace.reminders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.reminders.application.ReminderDeliveryStore.Claim;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class HttpReminderMailDeliveryTest {

    private final Claim claim = new Claim(UUID.randomUUID(), UUID.randomUUID(),
            "Example", "Engineer", "Follow up", Instant.parse("2030-10-03T00:00:00Z"),
            Instant.parse("2030-10-02T00:00:00Z"), "owner@example.test", UUID.randomUUID());

    @Test
    void rejectsUnsafeEndpointBeforeMakingRequest() {
        for (String endpoint : new String[] {"", "http://example.test/mail",
                "file:///tmp/mail", "https://user@example.test/mail", "not a url"}) {
            var delivery = new HttpReminderMailDelivery(mock(HttpClient.class),
                    new ObjectMapper(), endpoint, "", "https://jobtrace.test");
            assertThatThrownBy(() -> delivery.deliver(claim))
                    .isInstanceOf(HttpReminderMailDelivery.DeliveryUnavailableException.class);
        }
    }

    @Test
    void sendsThroughConfiguredHttpsWebhookAndRejectsProviderFailure() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        doReturn(response).when(client).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(200, 503);
        var delivery = new HttpReminderMailDelivery(client, new ObjectMapper(),
                "https://mail.example.test/delivery", "mail-secret", "https://jobtrace.test/");
        delivery.deliver(claim);
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertThat(request.getValue().uri().toString())
                .isEqualTo("https://mail.example.test/delivery");
        assertThat(request.getValue().headers().firstValue("authorization"))
                .contains("Bearer mail-secret");
        assertThatThrownBy(() -> delivery.deliver(claim))
                .isInstanceOf(HttpReminderMailDelivery.DeliveryUnavailableException.class);
    }
}
