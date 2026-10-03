package com.jobtrace.identityaccess.domain;

/** Field-specific transport-compatible password validation failure. */
public class AccountPasswordValidationException extends IllegalArgumentException {

    private final String field;

    public AccountPasswordValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
