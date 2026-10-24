package com.jobtrace.identityaccess.application;

import java.util.Optional;

/** Existing-schema persistence for a single-use recovery grant. */
public interface PasswordResetStore {
    void consumeAttempt(String source);
    Optional<String> findVerifiedOwner(String email);
    void issue(String ownerId, String identifier);
    boolean valid(String identifier);
    boolean consumeAndReplace(String identifier, String passwordHash);
}
