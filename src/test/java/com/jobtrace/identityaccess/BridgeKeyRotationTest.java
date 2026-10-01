package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.web.BridgeTokenVerifier;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BridgeKeyRotationTest {

    @Test
    void acceptsCurrentAndPreviousConfiguredKeys() {
        BridgeTokenVerifier verifier = verifier(BridgeTokenVerifierTest.properties());

        assertThat(verifier.verify(
                        BridgeTokenFixtures.validToken("owner-a"),
                        "GET",
                        "/api/analytics/summary",
                        BridgeTokenFixtures.REQUEST_ID)
                .keyId()).isEqualTo(BridgeTokenFixtures.CURRENT_KEY_ID);
        assertThat(verifier.verify(
                        BridgeTokenFixtures.token(
                                BridgeTokenFixtures.PREVIOUS_KEY_ID,
                                BridgeTokenFixtures.PREVIOUS_SECRET,
                                "owner-a",
                                Map.of()),
                        "GET",
                        "/api/analytics/summary",
                        BridgeTokenFixtures.REQUEST_ID)
                .keyId()).isEqualTo(BridgeTokenFixtures.PREVIOUS_KEY_ID);
    }

    @Test
    void rejectsAKeyAfterItIsRetired() {
        var base = BridgeTokenVerifierTest.properties();
        var currentOnly = new com.jobtrace.shared.config.JobTraceProperties.AuthBridge(
                true,
                base.issuer(),
                base.audience(),
                base.maxLifetimeSeconds(),
                base.clockSkewSeconds(),
                base.replayKeyPrefix(),
                List.of(base.keys().getFirst()));
        String previousToken = BridgeTokenFixtures.token(
                BridgeTokenFixtures.PREVIOUS_KEY_ID,
                BridgeTokenFixtures.PREVIOUS_SECRET,
                "owner-a",
                Map.of());

        assertThatThrownBy(() -> verifier(currentOnly).verify(
                        previousToken,
                        "GET",
                        "/api/analytics/summary",
                        BridgeTokenFixtures.REQUEST_ID))
                .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(BridgeAuthenticationFailure.UNKNOWN_KEY));
    }

    private static BridgeTokenVerifier verifier(
            com.jobtrace.shared.config.JobTraceProperties.AuthBridge properties) {
        return new BridgeTokenVerifier(
                new com.jobtrace.shared.config.JobTraceProperties(null, null, properties),
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC),
                JsonMapper.builder().build());
    }
}
