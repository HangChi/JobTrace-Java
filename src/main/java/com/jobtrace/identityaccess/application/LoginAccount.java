package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.InvalidCredentialsException;
import com.jobtrace.identityaccess.domain.LoginValidationException;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Validates a login and creates a session only after a successful credential check. */
@Service
public class LoginAccount {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,30}");
    private final LoginStore store;
    private final PasswordHasher passwords;

    public LoginAccount(LoginStore store, PasswordHasher passwords) {
        this.store = store;
        this.passwords = passwords;
    }

    public Result execute(Input input) {
        if (input == null) {
            throw new LoginValidationException("identifier");
        }
        String identifier = normalizeIdentifier(input.identifier());
        if (input.password() == null || input.password().isEmpty()
                || input.password().length() > 128) {
            throw new LoginValidationException("password");
        }
        LoginStore.Credential credential = store.findCredential(identifier,
                identifier.contains("@"));
        if (credential == null || credential.passwordHash() == null) {
            // Avoid a cheap account-existence timing signal without storing a dummy credential.
            passwords.hash(input.password());
            throw new InvalidCredentialsException();
        }
        boolean matches;
        try {
            matches = passwords.matches(input.password(), credential.passwordHash());
        } catch (IllegalArgumentException exception) {
            matches = false;
        }
        if (!matches || credential.disabled()) {
            throw new InvalidCredentialsException();
        }
        String redirect = safeReturnTo(input.returnTo());
        if (redirect == null) {
            redirect = "admin".equals(credential.role()) ? "/admin" : "/";
        }
        return new Result(redirect, store.createSession(credential.userId(), input.userAgent()));
    }

    private static String normalizeIdentifier(String raw) {
        if (raw == null || raw.length() > 254) {
            throw new LoginValidationException("identifier");
        }
        String value = raw.trim();
        if (value.contains("@")) {
            try { return EmailCodeAddress.normalize(value); }
            catch (RuntimeException exception) {
                throw new LoginValidationException("identifier");
            }
        }
        if (!USERNAME.matcher(value).matches()) {
            throw new LoginValidationException("identifier");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    static String safeReturnTo(String raw) {
        if (raw == null || !raw.startsWith("/") || raw.startsWith("//")
                || raw.contains("\\") || raw.chars().anyMatch(c -> c < 32)) {
            return null;
        }
        try {
            URI uri = URI.create(raw);
            if (uri.isAbsolute() || uri.getRawAuthority() != null) {
                return null;
            }
            return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record Input(String identifier, String password, String returnTo, String userAgent) {}
    public record Result(String redirectTarget, String sessionToken) {}
}
