package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountSession;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListAccountSessions {

    public static final int MAX_SESSION_ID_LENGTH = 255;

    private final AccountSessionReadQuery query;

    public ListAccountSessions(AccountSessionReadQuery query) {
        this.query = query;
    }

    public List<AccountSession> execute(String ownerId, String currentSessionId) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) {
            throw new IllegalArgumentException("ownerId must contain 1 through 128 characters");
        }
        if (currentSessionId == null
                || currentSessionId.isBlank()
                || currentSessionId.length() > MAX_SESSION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "currentSessionId must contain 1 through 255 characters");
        }
        return List.copyOf(query.listActive(ownerId, currentSessionId));
    }
}
