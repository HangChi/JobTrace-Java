package com.jobtrace.identityaccess.domain;

import java.time.Instant;

/** Safe projection of one currently valid browser session and its account. */
public record BrowserSession(
        String id,
        Instant expiresAt,
        String userId,
        String displayName,
        String image,
        String role) {
}
