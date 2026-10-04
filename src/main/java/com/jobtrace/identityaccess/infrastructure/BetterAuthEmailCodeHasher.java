package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.EmailVerificationCodeHasher;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Matches the legacy HMAC over purpose, owner, normalized address, and code. */
@Component
public final class BetterAuthEmailCodeHasher implements EmailVerificationCodeHasher {

    private static final String PURPOSE = "email_binding";
    private final String secret;

    public BetterAuthEmailCodeHasher(
            @Value("${jobtrace.email-binding.verification-secret:}") String secret) {
        this.secret = secret;
    }

    @Override
    public String hash(String ownerId, String email, String code) {
        if (secret == null || secret.length() < 32) {
            throw new AccountEmailBindingUnavailableException();
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String input = PURPOSE + '\0' + ownerId + '\0' + email + '\0' + code;
            return HexFormat.of().formatHex(hmac.doFinal(
                    input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AccountEmailBindingUnavailableException();
        }
    }
}
