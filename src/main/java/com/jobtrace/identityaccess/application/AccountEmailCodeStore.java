package com.jobtrace.identityaccess.application;

/** Persists a new owner-bound binding code using the legacy schema and limits. */
public interface AccountEmailCodeStore {

    void consumeSourceAttempt();

    void issue(String ownerId, String sessionId, String email, String codeHash);
}
