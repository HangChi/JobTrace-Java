package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountEmailBindingInvalidPasswordException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingResult;
import com.jobtrace.identityaccess.domain.AccountEmailBindingStateException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.InvalidEmailBindingCodeException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Proves owner password and a purpose-bound code before binding the email. */
@Service
public class BindAccountEmail {

    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$");
    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final AccountPasswordStore credentials;
    private final PasswordHasher passwordHasher;
    private final AccountEmailChangeRateLimiter rateLimiter;
    private final EmailVerificationCodeHasher codeHasher;
    private final AccountEmailBindingStore bindingStore;

    public BindAccountEmail(AccountPasswordStore credentials,
            PasswordHasher passwordHasher,
            AccountEmailChangeRateLimiter rateLimiter,
            EmailVerificationCodeHasher codeHasher,
            AccountEmailBindingStore bindingStore) {
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.rateLimiter = rateLimiter;
        this.codeHasher = codeHasher;
        this.bindingStore = bindingStore;
    }

    public AccountEmailBindingResult execute(String ownerId, String currentSessionId,
            String currentPassword, String email, String verificationCode) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) {
            throw new AccountEmailBindingStateException();
        }
        if (currentSessionId == null || currentSessionId.isBlank()
                || currentSessionId.length() > 255) {
            throw validation("currentSessionId", "当前会话无效。");
        }
        if (currentPassword == null || currentPassword.isEmpty()
                || currentPassword.length() > 128) {
            throw validation("currentPassword", "请输入当前密码。");
        }
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw validation("email", "请输入有效的邮箱地址。");
        }
        if (verificationCode == null
                || !CODE.matcher(verificationCode.trim()).matches()) {
            throw validation("verificationCode", "请输入 6 位邮箱验证码");
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String code = verificationCode.trim();
        rateLimiter.consumeChangeAttempt(ownerId);
        var credential = credentials.findCredential(ownerId, currentSessionId)
                .orElseThrow(AccountEmailBindingStateException::new);
        final boolean matches;
        try {
            matches = passwordHasher.matches(currentPassword, credential.passwordHash());
        } catch (RuntimeException exception) {
            throw new AccountEmailBindingStateException(exception);
        }
        if (!matches) {
            throw new AccountEmailBindingInvalidPasswordException();
        }
        String codeHash = codeHasher.hash(ownerId, normalizedEmail, code);
        final boolean bound;
        try {
            bound = bindingStore.bind(ownerId, currentSessionId,
                    credential.accountId(), credential.passwordHash(),
                    normalizedEmail, codeHash);
        } catch (DuplicateKeyException exception) {
            throw new AccountEmailConflictException();
        }
        if (!bound) {
            throw new InvalidEmailBindingCodeException();
        }
        return new AccountEmailBindingResult(normalizedEmail);
    }

    private static AccountEmailBindingValidationException validation(
            String field, String message) {
        return new AccountEmailBindingValidationException(field, message);
    }
}
