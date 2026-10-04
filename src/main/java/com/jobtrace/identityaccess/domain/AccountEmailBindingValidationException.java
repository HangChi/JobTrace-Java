package com.jobtrace.identityaccess.domain;

/** Field-specific validation failure for an email-binding request. */
public class AccountEmailBindingValidationException extends RuntimeException {

    private final String field;

    public AccountEmailBindingValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
