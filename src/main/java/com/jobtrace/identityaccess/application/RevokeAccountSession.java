package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountSessionNotFoundException;
import com.jobtrace.identityaccess.domain.SessionRevocationResult;
import org.springframework.stereotype.Service;

@Service
public class RevokeAccountSession {

    private final AccountSessionCommand command;

    public RevokeAccountSession(AccountSessionCommand command) {
        this.command = command;
    }

    public SessionRevocationResult execute(String ownerId, String sessionId) {
        requireIdentifier(ownerId, 128, "ownerId");
        requireIdentifier(sessionId, ListAccountSessions.MAX_SESSION_ID_LENGTH, "sessionId");
        if (!command.revoke(ownerId, sessionId)) {
            throw new AccountSessionNotFoundException();
        }
        return new SessionRevocationResult(true);
    }

    private static void requireIdentifier(String value, int maximumLength, String name) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must contain 1 through " + maximumLength + " characters");
        }
    }
}
