package com.jobtrace.identityaccess;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.jobtrace.shared.config.JobTraceProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public final class BridgeTokenFixtures {

    public static final Object REMOVE = new Object();

    public static final String CURRENT_KEY_ID = "2026-10-current";
    public static final String PREVIOUS_KEY_ID = "2026-09-previous";
    public static final String CURRENT_SECRET = secret("current-key-material");
    public static final String PREVIOUS_SECRET = secret("previous-key-material");
    public static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    public static final String REQUEST_ID = "0199a55c-9b00-7000-8000-000000000001";

    private BridgeTokenFixtures() {
    }

    public static String validToken(String subject) {
        return token(CURRENT_KEY_ID, CURRENT_SECRET, subject, Map.of());
    }

    public static JobTraceProperties jobTraceProperties() {
        return new JobTraceProperties(
                null,
                null,
                new JobTraceProperties.AuthBridge(
                        true,
                        "legacy-jobtrace",
                        "jobtrace-java",
                        30,
                        5,
                        "jobtrace:auth-bridge:replay",
                        java.util.List.of(
                                new JobTraceProperties.SigningKey(
                                        CURRENT_KEY_ID,
                                        CURRENT_SECRET),
                                new JobTraceProperties.SigningKey(
                                        PREVIOUS_KEY_ID,
                                        PREVIOUS_SECRET))));
    }

    public static String token(
            String keyId,
            String encodedSecret,
            String subject,
            Map<String, Object> overrides) {
        try {
            var values = new java.util.HashMap<String, Object>();
            values.put("ver", 1L);
            values.put("iss", "legacy-jobtrace");
            values.put("aud", "jobtrace-java");
            values.put("sub", subject);
            values.put("role", "user");
            values.put("av", 3L);
            values.put("rid", REQUEST_ID);
            values.put("mth", "GET");
            values.put("pth", "/api/analytics/summary");
            values.put("iat", Date.from(NOW));
            values.put("nbf", Date.from(NOW));
            values.put("exp", Date.from(NOW.plusSeconds(30)));
            values.put("jti", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
            overrides.forEach((name, value) -> {
                if (value == REMOVE) {
                    values.remove(name);
                } else {
                    values.put(name, value);
                }
            });

            var claims = new JWTClaimsSet.Builder();
            values.forEach(claims::claim);
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(keyId)
                            .build(),
                    claims.build());
            jwt.sign(new MACSigner(Base64.getUrlDecoder().decode(encodedSecret)));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create test assertion", exception);
        }
    }

    public static String randomRequestId() {
        return UUID.randomUUID().toString();
    }

    private static String secret(String label) {
        byte[] bytes = (label + "-0123456789abcdef0123456789abcdef")
                .substring(0, 32)
                .getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
