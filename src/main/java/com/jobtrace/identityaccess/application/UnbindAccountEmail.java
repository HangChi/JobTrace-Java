package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountEmailUnbindingResult;
import com.jobtrace.identityaccess.domain.AccountEmailUnbindingStateException;
import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import com.jobtrace.identityaccess.domain.InvalidCurrentPasswordException;
import org.springframework.stereotype.Service;

/** Proves a current credential before atomically clearing owner email state. */
@Service
public class UnbindAccountEmail {

    private final AccountPasswordStore credentials;
    private final PasswordHasher hasher;
    private final AccountEmailUnbindingStore store;

    public UnbindAccountEmail(AccountPasswordStore credentials,
            PasswordHasher hasher, AccountEmailUnbindingStore store) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.store = store;
    }

    public AccountEmailUnbindingResult execute(
            String ownerId, String currentSessionId, String currentPassword) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) {
            throw new AccountEmailUnbindingStateException();
        }
        if (currentSessionId == null || currentSessionId.isBlank()
                || currentSessionId.length() > 255) {
            throw new AccountPasswordValidationException(
                    "currentSessionId", "当前会话无效。");
        }
        if (currentPassword == null || currentPassword.isEmpty()
                || currentPassword.length() > 128) {
            throw new AccountPasswordValidationException(
                    "currentPassword", "请输入当前密码。");
        }
        store.consumeChangeAttempt(ownerId);
        var credential = credentials.findCredential(ownerId, currentSessionId)
                .orElseThrow(AccountEmailUnbindingStateException::new);
        final boolean matches;
        try {
            matches = hasher.matches(currentPassword, credential.passwordHash());
        } catch (RuntimeException exception) {
            throw new AccountEmailUnbindingStateException(exception);
        }
        if (!matches) {
            throw new InvalidCurrentPasswordException();
        }
        store.unbind(ownerId, currentSessionId,
                credential.accountId(), credential.passwordHash());
        return new AccountEmailUnbindingResult();
    }
}
