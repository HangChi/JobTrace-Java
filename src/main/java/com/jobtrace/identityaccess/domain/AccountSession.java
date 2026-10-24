package com.jobtrace.identityaccess.domain;

import java.util.Objects;

/** Active session projection exposed to its authenticated owner. */
public record AccountSession(
        String id,
        String createdAt,
        String expiresAt,
        String userAgent,
        boolean current) {

    public AccountSession {
        id = requireText(id, "id", 255);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " must contain 1 through " + maximumLength + " characters");
        }
        return value;
    }
}
