package com.jobtrace.identityaccess.domain;

/** Established response after clearing the recovery email. */
public record AccountEmailUnbindingResult(String email, boolean emailVerified) {

    public AccountEmailUnbindingResult() {
        this(null, false);
    }
}
