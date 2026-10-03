package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountPasswordChangeResult;
import com.jobtrace.identityaccess.domain.AccountPasswordStateException;
import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import com.jobtrace.identityaccess.domain.InvalidCurrentPasswordException;
import org.springframework.stereotype.Service;

/** Validates, verifies, and atomically replaces an owner's password. */
@Service
public class ChangeAccountPassword {

    private static final int MAX_OWNER_LENGTH = 128;
    private static final int MAX_SESSION_LENGTH = 255;
    private static final int MAX_CURRENT_PASSWORD_LENGTH = 128;
    private static final int MIN_NEW_PASSWORD_LENGTH = 8;
    private static final int MAX_NEW_PASSWORD_LENGTH = 16;

    private final AccountPasswordStore store;
    private final PasswordHasher hasher;

    public ChangeAccountPassword(AccountPasswordStore store, PasswordHasher hasher) {
        this.store = store;
        this.hasher = hasher;
    }

    public AccountPasswordChangeResult execute(
            String ownerId,
            String currentSessionId,
            String currentPassword,
            String newPassword) {
        requireInternalIdentifier(ownerId, MAX_OWNER_LENGTH, "ownerId");
        requireField(currentSessionId, 1, MAX_SESSION_LENGTH,
                "currentSessionId", "当前会话无效。");
        requireField(currentPassword, 1, MAX_CURRENT_PASSWORD_LENGTH,
                "currentPassword", "请输入当前密码");
        requireNewPassword(newPassword);

        var credential = store.findCredential(ownerId, currentSessionId)
                .orElseThrow(AccountPasswordStateException::new);
        final boolean matches;
        try {
            matches = hasher.matches(currentPassword, credential.passwordHash());
        } catch (RuntimeException exception) {
            throw new AccountPasswordStateException(exception);
        }
        if (!matches) {
            throw new InvalidCurrentPasswordException();
        }
        String replacementHash = hasher.hash(newPassword);
        store.replaceCredentialAndRevokeOtherSessions(
                ownerId,
                currentSessionId,
                credential.accountId(),
                credential.passwordHash(),
                replacementHash);
        return new AccountPasswordChangeResult();
    }

    private static void requireNewPassword(String value) {
        if (value == null || value.length() < MIN_NEW_PASSWORD_LENGTH) {
            throw new AccountPasswordValidationException(
                    "newPassword", "新密码至少需要 8 位");
        }
        if (value.length() > MAX_NEW_PASSWORD_LENGTH) {
            throw new AccountPasswordValidationException(
                    "newPassword", "新密码不能超过 16 位");
        }
    }

    private static void requireField(
            String value, int minimum, int maximum, String field, String message) {
        if (value == null || value.length() < minimum || value.length() > maximum) {
            throw new AccountPasswordValidationException(field, message);
        }
    }

    private static void requireInternalIdentifier(String value, int maximum, String name) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(
                    name + " must contain 1 through " + maximum + " characters");
        }
    }
}
