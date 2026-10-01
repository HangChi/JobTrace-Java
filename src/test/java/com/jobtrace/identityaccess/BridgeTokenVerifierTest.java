package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.web.BridgeTokenVerifier;
import com.jobtrace.shared.config.JobTraceProperties;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BridgeTokenVerifierTest {

    @Test
    void verifiesAValidRequestBoundAssertion() {
        var verifier = verifier();

        var assertion = verifier.verify(
                BridgeTokenFixtures.validToken("owner-a"),
                "GET",
                "/api/analytics/summary",
                BridgeTokenFixtures.REQUEST_ID);

        assertThat(assertion.identity())
                .isEqualTo(new BridgeIdentity("owner-a", BridgeIdentity.Role.USER, 3));
        assertThat(assertion.issuer()).isEqualTo("legacy-jobtrace");
        assertThat(assertion.expiresAt())
                .isEqualTo(BridgeTokenFixtures.NOW.plusSeconds(30));
        assertThat(assertion.keyId()).isEqualTo(BridgeTokenFixtures.CURRENT_KEY_ID);
    }

    @Test
    void rejectsMissingMalformedAndOversizedAssertions() {
        assertFailure(null, BridgeAuthenticationFailure.ABSENT);
        assertFailure("not-a-jws", BridgeAuthenticationFailure.MALFORMED);
        assertFailure("x".repeat(4097), BridgeAuthenticationFailure.OVERSIZED);
    }

    @Test
    void rejectsUnknownKeysAndInvalidSignatures() {
        String unknown = BridgeTokenFixtures.token(
                "retired",
                BridgeTokenFixtures.CURRENT_SECRET,
                "owner-a",
                Map.of());
        assertFailure(unknown, BridgeAuthenticationFailure.UNKNOWN_KEY);

        String signedByWrongSecret = BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID,
                BridgeTokenFixtures.PREVIOUS_SECRET,
                "owner-a",
                Map.of());
        assertFailure(signedByWrongSecret, BridgeAuthenticationFailure.INVALID_SIGNATURE);
    }

    @Test
    void rejectsIncompleteAndInvalidIdentityClaims() {
        assertFailure(token(Map.of("sub", BridgeTokenFixtures.REMOVE)),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("sub", " ")),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("role", "superuser")),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("av", -1L)),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("aud", "another-service")),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("iss", "another-issuer")),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("ver", 2L)),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of("extra", "not-v1")),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
    }

    @Test
    void rejectsPrematureExpiredAndOverlongAssertions() {
        assertFailure(token(Map.of(
                        "iat", java.util.Date.from(BridgeTokenFixtures.NOW.plusSeconds(6)),
                        "nbf", java.util.Date.from(BridgeTokenFixtures.NOW.plusSeconds(6)),
                        "exp", java.util.Date.from(BridgeTokenFixtures.NOW.plusSeconds(30)))),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of(
                        "iat", java.util.Date.from(BridgeTokenFixtures.NOW.minusSeconds(40)),
                        "nbf", java.util.Date.from(BridgeTokenFixtures.NOW.minusSeconds(40)),
                        "exp", java.util.Date.from(BridgeTokenFixtures.NOW.minusSeconds(6)))),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of(
                        "exp", java.util.Date.from(BridgeTokenFixtures.NOW.plusSeconds(31)))),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
        assertFailure(token(Map.of(
                        "nbf", java.util.Date.from(BridgeTokenFixtures.NOW.minusSeconds(1)))),
                BridgeAuthenticationFailure.INVALID_CLAIMS);
    }

    @Test
    void rejectsRequestMethodPathAndIdentifierMismatch() {
        assertFailure(
                BridgeTokenFixtures.validToken("owner-a"),
                "POST",
                "/api/analytics/summary",
                BridgeTokenFixtures.REQUEST_ID,
                BridgeAuthenticationFailure.REQUEST_MISMATCH);
        assertFailure(
                BridgeTokenFixtures.validToken("owner-a"),
                "GET",
                "/api/analytics/other",
                BridgeTokenFixtures.REQUEST_ID,
                BridgeAuthenticationFailure.REQUEST_MISMATCH);
        assertFailure(
                BridgeTokenFixtures.validToken("owner-a"),
                "GET",
                "/api/analytics/summary",
                BridgeTokenFixtures.randomRequestId(),
                BridgeAuthenticationFailure.REQUEST_MISMATCH);
        assertFailure(token(Map.of("rid", "not-a-uuid")),
                BridgeAuthenticationFailure.REQUEST_MISMATCH);
    }

    @Test
    void rejectsUnsupportedAlgorithmAndDuplicateJsonNames() {
        String valid = BridgeTokenFixtures.validToken("owner-a");
        String[] parts = valid.split("\\.");
        String unsupportedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS512\",\"kid\":\"2026-10-current\",\"typ\":\"JWT\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFailure(
                unsupportedHeader + "." + parts[1] + "." + parts[2],
                BridgeAuthenticationFailure.UNSUPPORTED_ALGORITHM);

        String duplicatePayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"owner-a\",\"sub\":\"owner-b\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFailure(
                parts[0] + "." + duplicatePayload + "." + parts[2],
                BridgeAuthenticationFailure.MALFORMED);
    }

    private static String token(Map<String, Object> overrides) {
        return BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID,
                BridgeTokenFixtures.CURRENT_SECRET,
                "owner-a",
                overrides);
    }

    private static BridgeTokenVerifier verifier() {
        return new BridgeTokenVerifier(
                jobTraceProperties(),
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC),
                JsonMapper.builder().build());
    }

    private static void assertFailure(
            String token,
            BridgeAuthenticationFailure expected) {
        assertFailure(
                token,
                "GET",
                "/api/analytics/summary",
                BridgeTokenFixtures.REQUEST_ID,
                expected);
    }

    private static void assertFailure(
            String token,
            String method,
            String path,
            String requestId,
            BridgeAuthenticationFailure expected) {
        assertThatThrownBy(() -> verifier().verify(token, method, path, requestId))
                .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(expected));
    }

    static JobTraceProperties.AuthBridge properties() {
        return new JobTraceProperties.AuthBridge(
                true,
                "legacy-jobtrace",
                "jobtrace-java",
                30,
                5,
                "jobtrace:auth-bridge:replay",
                List.of(
                        new JobTraceProperties.SigningKey(
                                BridgeTokenFixtures.CURRENT_KEY_ID,
                                BridgeTokenFixtures.CURRENT_SECRET),
                        new JobTraceProperties.SigningKey(
                                BridgeTokenFixtures.PREVIOUS_KEY_ID,
                                BridgeTokenFixtures.PREVIOUS_SECRET)));
    }

    static JobTraceProperties jobTraceProperties() {
        return new JobTraceProperties(null, null, properties());
    }
}
