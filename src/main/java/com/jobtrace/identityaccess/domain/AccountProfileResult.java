package com.jobtrace.identityaccess.domain;

import java.util.Objects;

/** Stable profile response after the durable update. */
public record AccountProfileResult(
        String id,
        String displayName,
        String image,
        String recoveryEmail,
        boolean emailVerified) {

    public AccountProfileResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
