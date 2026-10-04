package com.jobtrace.identityaccess.application;

/** Atomic verification-code and recovery-email update boundary. */
public interface AccountEmailBindingStore {

    /** Returns false only for an invalid code; a wrong active attempt is committed. */
    boolean bind(String ownerId, String currentSessionId, String accountId,
            String expectedPasswordHash, String normalizedEmail, String codeHash);
}
