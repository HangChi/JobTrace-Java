package com.jobtrace.identityaccess.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class HttpPasswordResetDeliveryTest {
    private final HttpClient client = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsOnlyToConfiguredHttpsWebhook() throws Exception {
        HttpResponse<?> response = mock(HttpResponse.class);
        doReturn(204).when(response).statusCode();
        doReturn(response).when(client).send(any(), any());
        new HttpPasswordResetDelivery(client, mapper, "https://mail.example.test/submit",
                "test-secret").deliver("owner@example.test", "https://jobtrace.test/reset");
        ArgumentCaptor<HttpRequest> captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captured.capture(), any());
        assertTrue(captured.getValue().uri().toString()
                .equals("https://mail.example.test/submit"));
        assertTrue(captured.getValue().headers().firstValue("authorization")
                .orElse("").equals("Bearer test-secret"));
    }

    @Test
    void invalidDestinationsCannotReceiveAnAddressOrToken() {
        for (String endpoint : new String[]{"", "http://mail.example.test/submit",
                "https://user@mail.example.test/submit", "https://"}) {
            assertThrows(HttpPasswordResetDelivery.DeliveryUnavailableException.class,
                    () -> new HttpPasswordResetDelivery(client, mapper, endpoint, "")
                            .deliver("owner@example.test", "https://jobtrace.test/reset"));
        }
    }

    @Test
    void remoteFailureIsReportedWithoutLoggingSensitiveContent() throws Exception {
        HttpResponse<?> response = mock(HttpResponse.class);
        doReturn(502).when(response).statusCode();
        doReturn(response).when(client).send(any(), any());
        var delivery = new HttpPasswordResetDelivery(client, mapper,
                "https://mail.example.test/submit", "");
        assertThrows(HttpPasswordResetDelivery.DeliveryUnavailableException.class,
                () -> delivery.deliver("owner@example.test", "https://jobtrace.test/reset"));
    }

    @Test
    void ioAndInterruptFailuresAreReported() throws Exception {
        doThrow(new IOException("offline")).when(client).send(any(), any());
        var delivery = new HttpPasswordResetDelivery(client, mapper,
                "https://mail.example.test/submit", "");
        assertThrows(HttpPasswordResetDelivery.DeliveryUnavailableException.class,
                () -> delivery.deliver("owner@example.test", "https://jobtrace.test/reset"));
        doThrow(new InterruptedException()).when(client).send(any(), any());
        try {
            assertThrows(HttpPasswordResetDelivery.DeliveryUnavailableException.class,
                    () -> delivery.deliver("owner@example.test", "https://jobtrace.test/reset"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
