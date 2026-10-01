package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.BridgeTokenFixtures;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.identityaccess.web.BridgeTokenVerifier;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IdentityBridgeContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void acceptsNodeCurrentAndPreviousKeyFixtures() throws Exception {
        Fixture fixture = fixture();
        BridgeTokenVerifier verifier = verifier();

        assertThat(verify(verifier, fixture, fixture.validCurrent()).keyId())
                .isEqualTo(BridgeTokenFixtures.CURRENT_KEY_ID);
        assertThat(verify(verifier, fixture, fixture.validPrevious()).keyId())
                .isEqualTo(BridgeTokenFixtures.PREVIOUS_KEY_ID);
    }

    @Test
    void rejectsNodeFixtureSignedWithAnUnconfiguredSecret() throws Exception {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> verify(verifier(), fixture, fixture.forgedCurrent()))
                .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(BridgeAuthenticationFailure.INVALID_SIGNATURE));
    }

    @Test
    void consumesTheNodeReplayFixtureExactlyOnce() throws Exception {
        Fixture fixture = fixture();
        Set<String> claimed = new HashSet<>();
        ReplayGuard replayGuard = new ReplayGuard() {
            @Override
            public boolean claim(String issuer, String tokenId, Instant retainUntil) {
                return claimed.add(issuer + ":" + tokenId);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        var useCase = new ClaimAssertionUseCase(
                verifier(),
                replayGuard,
                BridgeTokenFixtures.jobTraceProperties().authBridge());

        assertThat(useCase.execute(
                        fixture.replayed(),
                        fixture.method(),
                        fixture.path(),
                        fixture.requestId())
                .identity().subject()).isEqualTo("owner-a");
        assertThatThrownBy(() -> useCase.execute(
                        fixture.replayed(),
                        fixture.method(),
                        fixture.path(),
                        fixture.requestId()))
                .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(BridgeAuthenticationFailure.REPLAYED));
    }

    private static com.jobtrace.identityaccess.application.AssertionVerifier.VerifiedAssertion verify(
            BridgeTokenVerifier verifier,
            Fixture fixture,
            String token) {
        return verifier.verify(
                token,
                fixture.method(),
                fixture.path(),
                fixture.requestId());
    }

    private static BridgeTokenVerifier verifier() {
        return new BridgeTokenVerifier(
                BridgeTokenFixtures.jobTraceProperties(),
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC),
                JSON);
    }

    private static Fixture fixture() throws Exception {
        try (InputStream input = IdentityBridgeContractTest.class.getResourceAsStream(
                "/contracts/identity-bridge/node-v1-fixtures.json")) {
            assertThat(input).isNotNull();
            return JSON.readValue(input, Fixture.class);
        }
    }

    private record Fixture(
            String requestId,
            String method,
            String path,
            String validCurrent,
            String validPrevious,
            String forgedCurrent,
            String replayed) {
    }
}
