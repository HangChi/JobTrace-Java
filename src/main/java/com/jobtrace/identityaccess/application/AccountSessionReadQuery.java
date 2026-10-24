package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountSession;
import java.util.List;

/** Owner-scoped port for reading active sessions from the legacy schema. */
public interface AccountSessionReadQuery {

    List<AccountSession> listActive(String ownerId, String currentSessionId);
}
