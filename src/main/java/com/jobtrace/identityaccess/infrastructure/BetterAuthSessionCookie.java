package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
            String name = (secure ? "__Secure-" : "") + "better-auth.session_token";
            return name + "=" + URLEncoder.encode(token + "." + signature,
                    StandardCharsets.UTF_8) + "; Max-Age=604800; Path=/; HttpOnly; SameSite=Lax"
                    + (secure ? "; Secure" : "");
        } catch (java.security.GeneralSecurityException exception) {
            throw new LoginUnavailableException();
        }
    }
}
