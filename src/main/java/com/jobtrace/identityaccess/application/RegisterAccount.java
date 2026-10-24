package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.InvalidRegistrationCodeException;
import com.jobtrace.identityaccess.domain.RegistrationValidationException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Validates a registration request and exchanges its purpose-bound code for an account. */
@Service
public class RegisterAccount {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,30}");
    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final RegistrationStore store;
    private final RegistrationEmailCodeHasher codeHasher;
    private final PasswordHasher passwordHasher;

    public RegisterAccount(RegistrationStore store, RegistrationEmailCodeHasher codeHasher,
            PasswordHasher passwordHasher) {
        this.store = store;
        this.codeHasher = codeHasher;
        this.passwordHasher = passwordHasher;
    }

    public void execute(Input input) {
        if (input == null) {
            throw invalid("username", "请输入用户名。");
        }
        String username = username(input.username());
        String email = email(input.email());
        String code = code(input.verificationCode());
        String password = password(input.password());
        String displayName = displayName(input.displayName(), username);
        if (!store.verifyCode(email, codeHasher.hash(email, code))) {
            throw new InvalidRegistrationCodeException();
        }
        store.createAccount(username, email, passwordHasher.hash(password), displayName);
    }

    private static String username(String raw) {
        if (raw == null || !USERNAME.matcher(raw.trim()).matches()) {
            throw invalid("username", "用户名只能包含 3–30 位字母、数字和下划线。");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String email(String raw) {
        try {
            return EmailCodeAddress.normalize(raw);
        } catch (AccountEmailCodeValidationException exception) {
            throw invalid("email", "请输入有效的邮箱地址。");
        }
    }

    private static String code(String raw) {
        if (raw == null || !CODE.matcher(raw.trim()).matches()) {
            throw invalid("verificationCode", "请输入 6 位邮箱验证码。");
        }
        return raw.trim();
    }

    private static String password(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 16) {
            throw invalid("password", "密码长度必须为 8–16 位。");
        }
        return raw;
    }

    private static String displayName(String raw, String username) {
        if (raw == null || raw.isEmpty()) {
            return username;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw invalid("displayName", "请输入有效的昵称。");
        }
        return trimmed;
    }

    private static RegistrationValidationException invalid(String field, String message) {
        return new RegistrationValidationException(field, message);
    }

    public record Input(String username, String email, String verificationCode,
            String password, String displayName) {}
}
