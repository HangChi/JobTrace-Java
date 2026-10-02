package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.shared.config.JobTraceProperties.AuthBridge;
import com.jobtrace.shared.config.JobTraceProperties;
import com.jobtrace.shared.health.BridgeReplayAvailability;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Shared, atomic replay protection backed by Redis-compatible storage. */
@Component
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public final class RedisReplayGuard implements ReplayGuard, BridgeReplayAvailability {

    private final StringRedisTemplate redis;
    private final AuthBridge properties;
    private final Clock clock;

    public RedisReplayGuard(
            StringRedisTemplate redis,
            JobTraceProperties jobTraceProperties,
            Clock clock) {
        this.redis = redis;
        this.properties = jobTraceProperties.authBridge();
        this.clock = clock;
    }

    @Override
    public boolean claim(String issuer, String tokenId, Instant retainUntil) {
        Duration ttl = Duration.between(clock.instant(), retainUntil);
        if (ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        Boolean claimed = redis.opsForValue()
                .setIfAbsent(key(issuer, tokenId), "1", ttl);
        return Boolean.TRUE.equals(claimed);
    }

    @Override
    public boolean isAvailable() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            return connection.ping() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String key(String issuer, String tokenId) {
        return properties.replayKeyPrefix() + ":" + digest(issuer + "\0" + tokenId);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
