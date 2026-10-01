package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.AssertionVerifier;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClaimAssertionUseCaseTest {

    private static final AssertionVerifier VERIFIER = (token, method, path, requestId) ->
            new AssertionVerifier.VerifiedAssertion(
                    new BridgeIdentity("owner-a", BridgeIdentity.Role.USER, 1),
                    "legacy-jobtrace",
                    "one-time-id",
                    BridgeTokenFixtures.NOW.plusSeconds(30),
                    BridgeTokenFixtures.CURRENT_KEY_ID);

    @Test
    void failsClosedWhenReplayStoreReportsUnavailable() {
        assertFailure(new StubReplayGuard(false, true),
                BridgeAuthenticationFailure.REPLAY_STORE_UNAVAILABLE);
    }

    @Test
    void mapsReplayStoreExceptionsToUnavailable() {
        ReplayGuard throwing = new ReplayGuard() {
            @Override
            public boolean claim(String issuer, String tokenId, Instant retainUntil) {
                throw new IllegalStateException("store unavailable");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        assertFailure(throwing, BridgeAuthenticationFailure.REPLAY_STORE_UNAVAILABLE);
    }

    private static void assertFailure(
            ReplayGuard replayGuard,
            BridgeAuthenticationFailure expected) {
        var useCase = new ClaimAssertionUseCase(
                VERIFIER,
                replayGuard,
                BridgeTokenFixtures.jobTraceProperties().authBridge());

        assertThatThrownBy(() -> useCase.execute("token", "GET", "/path", "request"))
                .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(expected));
    }

    private record StubReplayGuard(boolean available, boolean claim) implements ReplayGuard {
        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean claim(String issuer, String tokenId, Instant retainUntil) {
            return claim;
        }
    }
}
