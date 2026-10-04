package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AvatarStore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Writes to the same Tencent COS bucket and signing contract as the old service. */
@Component
public final class TencentCosAvatarStore implements AvatarStore {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient client;
    private final String secretId;
    private final String secretKey;
    private final String bucket;
    private final String region;
    private final String publicBaseUrl;

    @Autowired
    public TencentCosAvatarStore(@Value("${COS_SECRET_ID:}") String secretId,
            @Value("${COS_SECRET_KEY:}") String secretKey,
            @Value("${COS_BUCKET:}") String bucket,
            @Value("${COS_REGION:}") String region,
            @Value("${COS_PUBLIC_BASE_URL:}") String publicBaseUrl) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), secretId, secretKey,
                bucket, region, publicBaseUrl);
    }

    TencentCosAvatarStore(HttpClient client, String secretId, String secretKey,
            String bucket, String region, String publicBaseUrl) {
        this.client = client;
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.region = region;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public String upload(String key, String contentType, byte[] body) {
        if (secretId.isBlank() || secretKey.isBlank()
                || !bucket.matches("[a-z0-9][a-z0-9-]*-[0-9]+")
                || !region.matches("[a-z]+-[a-z0-9-]+")
                || !key.matches("avatars/[A-Za-z0-9_-]{1,128}/[a-f0-9]{32}\\.(png|jpg|webp|gif)")) {
            throw new StorageUnavailableException();
        }
        String host = bucket + ".cos." + region + ".myqcloud.com";
        URI uri = URI.create("https://" + host + "/" + key);
        String publicBase = publicBaseUrl.isBlank() ? "https://" + host : publicBaseUrl;
        URI publicUri;
        try {
            publicUri = URI.create(publicBase);
            if (!"https".equals(publicUri.getScheme()) || publicUri.getHost() == null
                    || publicUri.getUserInfo() != null || publicUri.getQuery() != null
                    || publicUri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new StorageUnavailableException();
        }
        var request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                .header("Authorization", authorization(host, "/" + key, Instant.now()))
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new StorageUnavailableException();
            }
            return publicBase.replaceAll("/$", "") + "/" + key;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageUnavailableException();
        } catch (IOException exception) {
            throw new StorageUnavailableException();
        }
    }

    String authorization(String host, String path, Instant now) {
        long start = now.getEpochSecond() - 60;
        String keyTime = start + ";" + (start + 660);
        String httpString = "put\n" + path + "\n\nhost=" + host + "\n";
        String stringToSign = "sha1\n" + keyTime + "\n"
                + sha1(httpString.getBytes(StandardCharsets.UTF_8)) + "\n";
        // The legacy signer uses the first HMAC's lowercase hex text as the second key.
        String signKey = HexFormat.of().formatHex(
                hmac(secretKey.getBytes(StandardCharsets.UTF_8), keyTime));
        String signature = HexFormat.of().formatHex(
                hmac(signKey.getBytes(StandardCharsets.UTF_8), stringToSign));
        return "q-sign-algorithm=sha1&q-ak=" + secretId + "&q-sign-time=" + keyTime
                + "&q-key-time=" + keyTime + "&q-header-list=host&q-url-param-list="
                + "&q-signature=" + signature;
    }

    private static String sha1(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 unavailable", exception);
        }
    }

    private static byte[] hmac(byte[] key, String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("COS signing unavailable", exception);
        }
    }

    public static final class StorageUnavailableException extends RuntimeException {}
}
