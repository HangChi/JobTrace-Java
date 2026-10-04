package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signs exactly the installed Better Auth session-token cookie format. */
@Component
public class BetterAuthSessionCookie {
    private final String secret;
    private final boolean secure;

    public BetterAuthSessionCookie(@Value("${BETTER_AUTH_SECRET:}") String secret,
            @Value("${BETTER_AUTH_URL:}") String baseUrl,
            @Value("${JOBTRACE_AUTH_SECURE_COOKIES:false}") boolean forceSecure) {
        this.secret = secret;
        this.secure = forceSecure || baseUrl.startsWith("https://");
    }

    public void ensureAvailable() {
        if (secret == null || secret.isBlank()) {
            throw new LoginUnavailableException();
        }
    }

    public String header(String token) {
        ensureAvailable();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
            return name("session_token") + "=" + URLEncoder.encode(token + "." + signature,
                    StandardCharsets.UTF_8) + "; Max-Age=604800; Path=/; HttpOnly; SameSite=Lax"
                    + (secure ? "; Secure" : "");
        } catch (java.security.GeneralSecurityException exception) {
            throw new LoginUnavailableException();
        }
    }

    /** A cookie selects a logout session only after exact-name and HMAC verification. */
    public String verifiedToken(String cookieHeader) {
        ensureAvailable();
        if (cookieHeader == null || cookieHeader.length() > 32_768
                || cookieHeader.chars().anyMatch(value -> value < 32)) {
            return null;
        }
        String raw = null;
        for (String entry : cookieHeader.split(";", -1)) {
            String trimmed = entry.trim();
            int delimiter = trimmed.indexOf('=');
            if (delimiter > 0 && name("session_token").equals(trimmed.substring(0, delimiter))) {
                if (raw != null) {
                    return null;
                }
                raw = trimmed.substring(delimiter + 1);
            }
        }
        if (raw == null || raw.length() > 512) {
            return null;
        }
        try {
            String decoded = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
            int delimiter = decoded.indexOf('.');
            if (delimiter < 1 || delimiter != decoded.lastIndexOf('.')) {
                return null;
            }
            String token = decoded.substring(0, delimiter);
            if (token.length() > 256 || !token.matches("[A-Za-z0-9_-]+")) {
                return null;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = Base64.getEncoder().encode(
                    mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
            byte[] actual = decoded.substring(delimiter + 1).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, actual) ? token : null;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            return null;
        }
    }

    /** Expires every legacy auth cookie plus bounded session-data chunks present on the request. */
    public List<String> expiredHeaders(String cookieHeader) {
        ensureAvailable();
        Set<String> names = new LinkedHashSet<>();
        names.add(name("session_token"));
        names.add(name("session_data"));
        names.add(name("dont_remember"));
        if (cookieHeader != null && cookieHeader.length() <= 32_768) {
            String prefix = name("session_data") + ".";
            for (String entry : cookieHeader.split(";", -1)) {
                String trimmed = entry.trim();
                int delimiter = trimmed.indexOf('=');
                if (delimiter > prefix.length()) {
                    String candidate = trimmed.substring(0, delimiter);
                    String suffix = candidate.startsWith(prefix)
                            ? candidate.substring(prefix.length()) : "";
                    if (suffix.matches("[0-9]{1,3}") && names.size() < 35) {
                        names.add(candidate);
                    }
                }
            }
        }
        return names.stream().map(value -> value
                + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : ""))
                .toList();
    }

    private String name(String suffix) {
        return (secure ? "__Secure-" : "") + "better-auth." + suffix;
    }
}
