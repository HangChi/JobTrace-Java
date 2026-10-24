package com.jobtrace.identityaccess.application;

/** Shared owner-based limit for email binding and unbinding. */
public interface AccountEmailChangeRateLimiter {

    void consumeChangeAttempt(String ownerId);
}
