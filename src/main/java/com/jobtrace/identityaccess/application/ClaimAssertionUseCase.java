package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.shared.config.JobTraceProperties.AuthBridge;
import java.time.Instant;

/** Validates an assertion and atomically consumes it before exposing its identity. */
public final class ClaimAssertionUseCase {

    private final AssertionVerifier verifier;
    private final ReplayGuard replayGuard;
    private final AuthBridge properties;

    public ClaimAssertionUseCase(
            AssertionVerifier verifier,
            ReplayGuard replayGuard,
            AuthBridge properties) {
        this.verifier = verifier;
        this.replayGuard = replayGuard;
        this.properties = properties;
    }

    public AssertionVerifier.VerifiedAssertion execute(
            String token,
            String method,
            String path,
            String requestId) {
        AssertionVerifier.VerifiedAssertion assertion =
                verifier.verify(token, method, path, requestId);
        Instant retainUntil = assertion.expiresAt()
                .plusSeconds(properties.clockSkewSeconds());
        try {
            if (!replayGuard.isAvailable()) {
                throw failure(BridgeAuthenticationFailure.REPLAY_STORE_UNAVAILABLE);
            }
            if (!replayGuard.claim(assertion.issuer(), assertion.tokenId(), retainUntil)) {
                throw failure(BridgeAuthenticationFailure.REPLAYED);
            }
        } catch (BridgeAuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BridgeAuthenticationException(
                    BridgeAuthenticationFailure.REPLAY_STORE_UNAVAILABLE,
                    exception);
        }
        return assertion;
    }

    private static BridgeAuthenticationException failure(
            BridgeAuthenticationFailure failure) {
        return new BridgeAuthenticationException(failure);
    }
}
