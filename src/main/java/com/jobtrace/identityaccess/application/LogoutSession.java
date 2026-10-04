package com.jobtrace.identityaccess.application;

import org.springframework.stereotype.Service;

/** Deletes one session selected by an already verified browser token. */
@Service
public class LogoutSession {
    private final LogoutSessionStore store;

    public LogoutSession(LogoutSessionStore store) {
        this.store = store;
    }

    public boolean execute(String verifiedToken) {
        if (verifiedToken == null || verifiedToken.isBlank()) {
            return false;
        }
        return store.deleteByToken(verifiedToken);
    }
}
