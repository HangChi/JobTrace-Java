package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import java.time.Instant;

/** Port for validating a request-bound signed identity assertion. */
public interface AssertionVerifier {

    VerifiedAssertion verify(String token, String method, String path, String requestId);

    record VerifiedAssertion(
            BridgeIdentity identity,
            String issuer,
            String tokenId,
            Instant expiresAt,
            String keyId) {
    }
}
