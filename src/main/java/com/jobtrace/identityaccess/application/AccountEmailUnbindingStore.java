package com.jobtrace.identityaccess.application;

/** Rate-limit and atomic owner-scoped email clearing boundary. */
public interface AccountEmailUnbindingStore {

    void consumeChangeAttempt(String ownerId);

    void unbind(String ownerId, String currentSessionId,
            String accountId, String expectedPasswordHash);
}
