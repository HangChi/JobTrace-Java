package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Issues a legacy-compatible code without exposing it to the caller. */
@Service
public class RequestAccountEmailCode {

    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$");

    private final AccountEmailCodeStore store;
    private final AccountEmailCodeGenerator generator;
    private final EmailVerificationCodeHasher hasher;
    private final AccountEmailCodeDelivery delivery;

    public RequestAccountEmailCode(AccountEmailCodeStore store,
            AccountEmailCodeGenerator generator, EmailVerificationCodeHasher hasher,
            AccountEmailCodeDelivery delivery) {
        this.store = store;
        this.generator = generator;
        this.hasher = hasher;
        this.delivery = delivery;
    }

    public void execute(String ownerId, String sessionId, String email) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) {
            throw new AccountEmailCodeStateException();
        }
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 255) {
            throw new AccountEmailCodeValidationException("currentSessionId");
        }
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new AccountEmailCodeValidationException("email");
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        delivery.ensureAvailable();
        store.consumeSourceAttempt();
        String code = generator.generate();
        String hash = hasher.hash(ownerId, normalizedEmail, code);
        store.issue(ownerId, sessionId, normalizedEmail, hash);
        delivery.deliver(normalizedEmail, code);
    }
}
