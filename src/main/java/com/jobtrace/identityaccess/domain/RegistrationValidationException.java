package com.jobtrace.identityaccess.domain;

/** Field-specific invalid registration input without echoing submitted values. */
public final class RegistrationValidationException extends RuntimeException {

    private final String field;

    public RegistrationValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
