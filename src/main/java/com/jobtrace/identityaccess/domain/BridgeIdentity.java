package com.jobtrace.identityaccess.domain;

import java.util.Locale;

/** An identity accepted from a completely validated server-to-server assertion. */
public record BridgeIdentity(String subject, Role role, long accessVersion) {

    public BridgeIdentity {
        if (subject == null || subject.isBlank() || subject.length() > 128) {
            throw new IllegalArgumentException("subject must contain 1 through 128 characters");
        }
        subject = subject.trim();
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (accessVersion < 0) {
            throw new IllegalArgumentException("accessVersion must not be negative");
        }
    }

    public enum Role {
        USER,
        ADMIN;

        public static Role fromClaim(String value) {
            if (value == null) {
                throw new IllegalArgumentException("role must not be null");
            }
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
