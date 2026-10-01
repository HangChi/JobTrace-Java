package com.jobtrace.identityaccess.domain;

import java.time.Instant;

/** Atomically prevents a signed assertion from being accepted more than once. */
public interface ReplayGuard {

    boolean claim(String issuer, String tokenId, Instant retainUntil);

    boolean isAvailable();
}
