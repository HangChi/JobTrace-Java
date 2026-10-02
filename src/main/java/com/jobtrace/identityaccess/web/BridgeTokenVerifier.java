package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.AssertionVerifier;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.shared.config.JobTraceProperties;
import com.jobtrace.shared.config.JobTraceProperties.AuthBridge;
import com.jobtrace.shared.config.JobTraceProperties.SigningKey;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates the cryptographic and request-bound parts of bridge contract v1. */
@Component
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public final class BridgeTokenVerifier implements AssertionVerifier {

    public static final int MAX_TOKEN_LENGTH = 4096;
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MIN_TOKEN_ID_BYTES = 16;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final AuthBridge properties;
    private final Clock clock;
    private final ObjectMapper strictMapper;
    private final Map<String, byte[]> keys;

    public BridgeTokenVerifier(
            JobTraceProperties jobTraceProperties,
            Clock clock,
            ObjectMapper objectMapper) {
        this.properties = jobTraceProperties.authBridge();
        this.clock = clock;
        this.strictMapper = objectMapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        try {
            this.keys = properties.keys().stream().collect(Collectors.toUnmodifiableMap(
                    SigningKey::id,
                    key -> decodeSecret(key.secret())));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid bridge signing-key configuration", exception);
        }
    }

    @Override
    public VerifiedAssertion verify(
            String token,
            String method,
            String path,
            String requestId) {
        if (token == null || token.isBlank()) {
            throw failure(BridgeAuthenticationFailure.ABSENT);
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw failure(BridgeAuthenticationFailure.OVERSIZED);
        }

        try {
            String[] segments = token.split("\\.", -1);
            if (segments.length != 3) {
                throw failure(BridgeAuthenticationFailure.MALFORMED);
            }
            JsonNode headerJson = decodeJson(segments[0]);
            JsonNode claimsJson = decodeJson(segments[1]);
            SignedJWT jwt = SignedJWT.parse(token);
            validateHeader(jwt, headerJson);
            byte[] key = keys.get(jwt.getHeader().getKeyID());
            if (key == null) {
                throw failure(BridgeAuthenticationFailure.UNKNOWN_KEY);
            }
            if (!jwt.verify(new MACVerifier(key))) {
                throw failure(BridgeAuthenticationFailure.INVALID_SIGNATURE);
            }
            return validateClaims(
                    claimsJson,
                    method,
                    path,
                    requestId,
                    jwt.getHeader().getKeyID());
        } catch (BridgeAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BridgeAuthenticationException(
                    BridgeAuthenticationFailure.MALFORMED,
                    exception);
        }
    }

    private void validateHeader(SignedJWT jwt, JsonNode headerJson) {
        if (!headerJson.isObject()
                || headerJson.size() != 3
                || !headerJson.has("alg")
                || !headerJson.has("kid")
                || !headerJson.has("typ")
                || !JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
            throw failure(BridgeAuthenticationFailure.UNSUPPORTED_ALGORITHM);
        }
        String keyId = jwt.getHeader().getKeyID();
        if (!JOSEObjectType.JWT.equals(jwt.getHeader().getType())
                || keyId == null
                || !KEY_ID.matcher(keyId).matches()
                || (jwt.getHeader().getCriticalParams() != null
                && !jwt.getHeader().getCriticalParams().isEmpty())) {
            throw failure(BridgeAuthenticationFailure.MALFORMED);
        }
    }

    private VerifiedAssertion validateClaims(
            JsonNode claims,
            String method,
            String path,
            String requestId,
            String keyId) {
        if (!claims.isObject()) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
        if (claims.size() != 13) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
        long version = integer(claims, "ver");
        String issuer = text(claims, "iss");
        String audience = text(claims, "aud");
        String subject = text(claims, "sub");
        String role = text(claims, "role");
        long accessVersion = integer(claims, "av");
        String assertedRequestId = text(claims, "rid");
        String assertedMethod = text(claims, "mth");
        String assertedPath = text(claims, "pth");
        Instant issuedAt = instant(claims, "iat");
        Instant notBefore = instant(claims, "nbf");
        Instant expiresAt = instant(claims, "exp");
        String tokenId = text(claims, "jti");

        validateIdentityClaims(version, issuer, audience, accessVersion, tokenId);
        validateRequestBinding(
                assertedRequestId,
                assertedMethod,
                assertedPath,
                requestId,
                method,
                path);
        validateTimes(issuedAt, notBefore, expiresAt);

        BridgeIdentity identity;
        try {
            identity = new BridgeIdentity(
                    subject,
                    BridgeIdentity.Role.fromClaim(role),
                    accessVersion);
        } catch (RuntimeException exception) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
        return new VerifiedAssertion(identity, issuer, tokenId, expiresAt, keyId);
    }

    private void validateIdentityClaims(
            long version,
            String issuer,
            String audience,
            long accessVersion,
            String tokenId) {
        if (version != 1
                || !properties.issuer().equals(issuer)
                || !properties.audience().equals(audience)
                || accessVersion < 0
                || !hasMinimumRandomness(tokenId)) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
    }

    private void validateRequestBinding(
            String assertedRequestId,
            String assertedMethod,
            String assertedPath,
            String requestId,
            String method,
            String path) {
        try {
            UUID.fromString(assertedRequestId);
        } catch (IllegalArgumentException exception) {
            throw failure(BridgeAuthenticationFailure.REQUEST_MISMATCH);
        }
        if (!assertedRequestId.equals(requestId)
                || !assertedMethod.equals(method)
                || !assertedPath.equals(path)) {
            throw failure(BridgeAuthenticationFailure.REQUEST_MISMATCH);
        }
    }

    private void validateTimes(
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt) {
        Instant now = clock.instant();
        long skew = properties.clockSkewSeconds();
        long lifetime = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
        if (lifetime < 1
                || lifetime > properties.maxLifetimeSeconds()
                || notBefore.isBefore(issuedAt)
                || notBefore.isAfter(expiresAt)
                || issuedAt.isAfter(now.plusSeconds(skew))
                || notBefore.isAfter(now.plusSeconds(skew))
                || expiresAt.isBefore(now.minusSeconds(skew))) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
    }

    private JsonNode decodeJson(String segment) throws Exception {
        byte[] json = Base64.getUrlDecoder().decode(segment);
        return strictMapper.readTree(json);
    }

    private String text(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
        return value.textValue();
    }

    private long integer(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw failure(BridgeAuthenticationFailure.INVALID_CLAIMS);
        }
        return value.longValue();
    }

    private Instant instant(JsonNode claims, String name) {
        return Instant.ofEpochSecond(integer(claims, name));
    }

    private boolean hasMinimumRandomness(String tokenId) {
        try {
            return Base64.getUrlDecoder().decode(tokenId).length >= MIN_TOKEN_ID_BYTES;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] decodeSecret(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("Bridge signing keys require at least 32 bytes");
        }
        return decoded;
    }

    private static BridgeAuthenticationException failure(
            BridgeAuthenticationFailure failure) {
        return new BridgeAuthenticationException(failure);
    }

}
