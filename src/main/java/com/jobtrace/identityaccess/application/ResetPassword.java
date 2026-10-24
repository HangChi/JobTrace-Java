package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import org.springframework.stereotype.Service;

/** Validates before hashing and atomically consumes the reset grant with credential replacement. */
@Service
public final class ResetPassword {
    private final PasswordResetStore store;
    private final PasswordHasher hasher;

    public ResetPassword(PasswordResetStore store, PasswordHasher hasher) {
        this.store = store;
        this.hasher = hasher;
    }

    public boolean valid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{24}")
                && store.valid(RequestPasswordReset.identifier(token));
    }

    public void execute(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 16) {
            throw new AccountPasswordValidationException(
                    "newPassword", "新密码长度须为 8 至 16 位");
        }
        if (token == null || !token.matches("[A-Za-z0-9_-]{24}")) {
            throw new InvalidResetTokenException();
        }
        // Expensive hashing happens before the row lock, but never changes state on failure.
        String hash = hasher.hash(newPassword);
        if (!store.consumeAndReplace(RequestPasswordReset.identifier(token), hash)) {
            throw new InvalidResetTokenException();
        }
    }

    public static final class InvalidResetTokenException extends RuntimeException {}
}
