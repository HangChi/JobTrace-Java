package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** Returns an owner only when the principal carries matching, verified bridge details. */
public final class BridgePrincipalOwner {

    private BridgePrincipalOwner() {}

    public static String require(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getDetails() instanceof BridgeIdentity identity
                && identity.subject().equals(authentication.getName())) {
            return identity.subject();
        }
        throw new Problem("unauthorized", "Authentication is required.", HttpStatus.UNAUTHORIZED);
    }
}
