package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountPasswordCredential;
import java.util.Optional;

/** Owner/session-scoped credential read and atomic replacement boundary. */
public interface AccountPasswordStore {

    Optional<AccountPasswordCredential> findCredential(
            String ownerId, String currentSessionId);

    void replaceCredentialAndRevokeOtherSessions(
            String ownerId,
            String currentSessionId,
            String accountId,
            String expectedPasswordHash,
            String replacementPasswordHash);
}
