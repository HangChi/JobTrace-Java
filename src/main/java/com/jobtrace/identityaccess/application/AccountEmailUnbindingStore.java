package com.jobtrace.identityaccess.application;

/** Rate-limit and atomic owner-scoped email clearing boundary. */
public interface AccountEmailUnbindingStore extends AccountEmailChangeRateLimiter {

    void unbind(String ownerId, String currentSessionId,
            String accountId, String expectedPasswordHash);
}
