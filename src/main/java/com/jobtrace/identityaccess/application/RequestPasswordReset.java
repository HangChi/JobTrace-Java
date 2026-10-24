package com.jobtrace.identityaccess.application;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues a legacy Better Auth reset grant without disclosing address membership. */
@Service
public final class RequestPasswordReset {
    private static final char[] TOKEN_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestPasswordReset.class);
    private final PasswordResetStore store;
    private final PasswordResetDelivery delivery;
    private final SecureRandom random = new SecureRandom();
    private final String baseUrl;

    public RequestPasswordReset(PasswordResetStore store, PasswordResetDelivery delivery,
            @Value("${BETTER_AUTH_URL:}") String baseUrl) {
        this.store = store;
        this.delivery = delivery;
        this.baseUrl = baseUrl;
    }

    public void execute(String address, String source) {
        String email = EmailCodeAddress.normalize(address);
        requireBaseUrl();
        store.consumeAttempt(source);
        store.findVerifiedOwner(email).ifPresent(owner -> {
            String token = token();
            store.issue(owner, identifier(token));
            try {
                delivery.deliver(email, baseUrl.replaceAll("/$", "")
                        + "/api/auth/reset-password/" + token
                        + "?callbackURL=%2Freset-password");
            } catch (RuntimeException exception) {
                // An address-dependent public error would disclose account membership.
                LOGGER.warn("Password reset delivery failed");
            }
        });
    }

    private void requireBaseUrl() {
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme())
                    || ("http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()))))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new RecoveryUnavailableException();
        }
    }

    private String token() {
        char[] chars = new char[24];
        for (int index = 0; index < chars.length; index++) {
            chars[index] = TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)];
        }
        return new String(chars);
    }

    public static String identifier(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    ("reset-password:" + token).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static final class RecoveryUnavailableException extends RuntimeException {}
}
