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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void concurrentClaimsExposeTheSameAssertionAtMostOnce() throws Exception {
        AtomicBoolean claimed = new AtomicBoolean();
        ReplayGuard atomicGuard = new ReplayGuard() {
            @Override
            public boolean claim(String issuer, String tokenId, Instant retainUntil) {
                return claimed.compareAndSet(false, true);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        var useCase = new ClaimAssertionUseCase(VERIFIER, atomicGuard,
                BridgeTokenFixtures.jobTraceProperties().authBridge());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfter(start, useCase));
            var second = executor.submit(() -> claimAfter(start, useCase));
            start.countDown();
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("accepted", "REPLAYED");
        }
    }

    private static String claimAfter(CountDownLatch start, ClaimAssertionUseCase useCase)
            throws InterruptedException {
        start.await();
        try {
            useCase.execute("token", "GET", "/path", "request");
            return "accepted";
        } catch (BridgeAuthenticationException exception) {
            return exception.failure().name();
        }
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
