package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.domain.BrowserIdentity;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** Requires administrator authority from the same verified bridge identity as the principal. */
public final class BridgePrincipalAdmin {

    private BridgePrincipalAdmin() {}

    public static String require(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            throw new Problem("unauthorized", "Authentication is required.",
                    HttpStatus.UNAUTHORIZED);
        }
        Object details = authentication.getDetails();
        if (details instanceof BrowserIdentity browser
                && browser.subject().equals(authentication.getName())) {
            if (browser.role() != BrowserIdentity.Role.ADMIN) {
                throw new Problem("forbidden", "需要管理员权限。", HttpStatus.FORBIDDEN);
            }
            return browser.subject();
        }
        if (!(details instanceof BridgeIdentity identity)
                || !identity.subject().equals(authentication.getName())) {
            throw new Problem("unauthorized", "Authentication is required.",
                    HttpStatus.UNAUTHORIZED);
        }
        if (identity.role() != BridgeIdentity.Role.ADMIN) {
            throw new Problem("forbidden", "需要管理员权限。", HttpStatus.FORBIDDEN);
        }
        return identity.subject();
    }
}
