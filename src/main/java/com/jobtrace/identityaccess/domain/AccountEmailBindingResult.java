package com.jobtrace.identityaccess.domain;

/** Established successful binding projection. */
public record AccountEmailBindingResult(String email, boolean emailVerified) {

    public AccountEmailBindingResult(String email) {
        this(email, true);
    }
}
