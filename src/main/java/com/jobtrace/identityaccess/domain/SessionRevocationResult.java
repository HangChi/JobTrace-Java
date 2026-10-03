package com.jobtrace.identityaccess.domain;

/** Stable response returned only after one owned session was deleted. */
public record SessionRevocationResult(boolean revoked) {

    public SessionRevocationResult {
        if (!revoked) {
            throw new IllegalArgumentException("A revocation result must confirm revocation");
        }
    }
}
