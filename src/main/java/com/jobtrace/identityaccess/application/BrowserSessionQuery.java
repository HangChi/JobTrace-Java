package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.BrowserSession;
import java.util.Optional;

/** Looks up a signed-cookie token without exposing it to response or logging code. */
public interface BrowserSessionQuery {
    Optional<BrowserSession> findActive(String token);
}
