package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared legacy-compatible address validation for code issuance. */
public final class EmailCodeAddress {

    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$");

    private EmailCodeAddress() {}

    public static String normalize(String email) {
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new AccountEmailCodeValidationException("email");
        }
        return email.toLowerCase(Locale.ROOT);
    }
}
