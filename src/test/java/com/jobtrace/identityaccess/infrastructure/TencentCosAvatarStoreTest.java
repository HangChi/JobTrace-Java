package com.jobtrace.identityaccess.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TencentCosAvatarStoreTest {
    @Test
    void signerMatchesInstalledJavaScriptCosContract() {
        var store = new TencentCosAvatarStore("id", "secret", "bucket-123", "ap-shanghai", "");
        assertEquals("q-sign-algorithm=sha1&q-ak=id"
                + "&q-sign-time=1700000000;1700000660"
                + "&q-key-time=1700000000;1700000660"
                + "&q-header-list=host&q-url-param-list="
                + "&q-signature=3bb9fd4354bdd1caaf56df0a62b90f4b78b3e160",
                store.authorization("bucket-123.cos.ap-shanghai.myqcloud.com",
                        "/avatars/owner/abc.png", Instant.ofEpochSecond(1700000060)));
    }

    @Test
    void missingConfigurationCannotSendAnUpload() {
        var store = new TencentCosAvatarStore("", "", "bucket-123", "ap-shanghai", "");
        assertThrows(TencentCosAvatarStore.StorageUnavailableException.class,
                () -> store.upload("avatars/owner/00000000000000000000000000000000.png",
                        "image/png", new byte[]{1}));
    }

    @Test
    void successfulUploadUsesConfiguredBucketAndPublicUrl() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<?> response = mock(HttpResponse.class);
        doReturn(200).when(response).statusCode();
        doReturn(response).when(client).send(any(), any());
        var store = new TencentCosAvatarStore(client, "id", "secret", "bucket-123",
                "ap-shanghai", "https://images.example.test");
        String key = "avatars/owner/00000000000000000000000000000000.png";
        assertEquals("https://images.example.test/" + key,
                store.upload(key, "image/png", new byte[]{1}));
        ArgumentCaptor<HttpRequest> captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captured.capture(), any());
        assertEquals("https://bucket-123.cos.ap-shanghai.myqcloud.com/" + key,
                captured.getValue().uri().toString());
        assertTrue(captured.getValue().headers().firstValue("authorization")
                .orElse("").startsWith("q-sign-algorithm=sha1"));
    }

    @Test
    void invalidSettingsAndRemoteFailuresCannotReturnAUrl() throws Exception {
        HttpClient client = mock(HttpClient.class);
        String key = "avatars/owner/00000000000000000000000000000000.png";
        for (String bucket : new String[]{"", "unsafe.example.test", "bucket-123/other"}) {
            var invalid = new TencentCosAvatarStore(client, "id", "secret", bucket,
                    "ap-shanghai", "");
            assertThrows(TencentCosAvatarStore.StorageUnavailableException.class,
                    () -> invalid.upload(key, "image/png", new byte[]{1}));
        }
        var invalidUrl = new TencentCosAvatarStore(client, "id", "secret", "bucket-123",
                "ap-shanghai", "http://images.example.test");
        assertThrows(TencentCosAvatarStore.StorageUnavailableException.class,
                () -> invalidUrl.upload(key, "image/png", new byte[]{1}));
        HttpResponse<?> response = mock(HttpResponse.class);
        doReturn(503).when(response).statusCode();
        doReturn(response).when(client).send(any(), any());
        var store = new TencentCosAvatarStore(client, "id", "secret", "bucket-123",
                "ap-shanghai", "");
        assertThrows(TencentCosAvatarStore.StorageUnavailableException.class,
                () -> store.upload(key, "image/png", new byte[]{1}));
        doThrow(new IOException("offline")).when(client).send(any(), any());
        assertThrows(TencentCosAvatarStore.StorageUnavailableException.class,
                () -> store.upload(key, "image/png", new byte[]{1}));
    }
}
