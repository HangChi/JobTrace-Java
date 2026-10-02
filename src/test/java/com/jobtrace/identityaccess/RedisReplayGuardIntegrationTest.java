package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.infrastructure.RedisReplayGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisReplayGuardIntegrationTest {

    @Container
    private static final GenericContainer<?> VALKEY = new GenericContainer<>(
            DockerImageName.parse("valkey/valkey:8-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                VALKEY.getHost(),
                VALKEY.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void atomicallyAcceptsFirstUseAndRejectsDuplicate() {
        RedisReplayGuard guard = guard(Clock.systemUTC());
        Instant retainUntil = Instant.now().plusSeconds(30);

        assertThat(guard.claim("issuer", "single-use", retainUntil)).isTrue();
        assertThat(guard.claim("issuer", "single-use", retainUntil)).isFalse();
        assertThat(guard.isAvailable()).isTrue();
    }

    @Test
    void permitsExactlyOneWinnerDuringAConcurrentRace() throws Exception {
        RedisReplayGuard guard = guard(Clock.systemUTC());
        Instant retainUntil = Instant.now().plusSeconds(30);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            attempts.add(() -> guard.claim("issuer", "racing-use", retainUntil));
        }

        try (var executor = Executors.newFixedThreadPool(12)) {
            long winners = executor.invokeAll(attempts).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .count();
            assertThat(winners).isEqualTo(1);
        }
    }

    @Test
    void replayStateExpiresAfterItsBoundedTtl() throws Exception {
        RedisReplayGuard guard = guard(Clock.systemUTC());

        assertThat(guard.claim(
                "issuer",
                "expiring-use",
                Instant.now().plusMillis(250))).isTrue();
        Thread.sleep(Duration.ofMillis(400));

        assertThat(guard.claim(
                "issuer",
                "expiring-use",
                Instant.now().plusSeconds(1))).isTrue();
    }

    @Test
    void reportsOutageAndFailsTheClaim() {
        LettuceConnectionFactory unavailableFactory =
                new LettuceConnectionFactory("127.0.0.1", 1);
        unavailableFactory.afterPropertiesSet();
        StringRedisTemplate unavailableRedis = new StringRedisTemplate(unavailableFactory);
        unavailableRedis.afterPropertiesSet();
        RedisReplayGuard guard = new RedisReplayGuard(
                unavailableRedis,
                BridgeTokenVerifierTest.jobTraceProperties(),
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC));
        try {
            assertThat(guard.isAvailable()).isFalse();
            assertThatThrownBy(() -> guard.claim(
                    "issuer",
                    "outage",
                    BridgeTokenFixtures.NOW.plusSeconds(30)))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            unavailableFactory.destroy();
        }
    }

    @Test
    void rejectsAClaimWhoseRetentionWindowAlreadyEnded() {
        RedisReplayGuard guard = guard(
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC));

        assertThat(guard.claim(
                "issuer",
                "already-expired",
                BridgeTokenFixtures.NOW)).isFalse();
        assertThat(guard.claim(
                "issuer",
                "negative-ttl",
                BridgeTokenFixtures.NOW.minusSeconds(1))).isFalse();
    }

    private static RedisReplayGuard guard(Clock clock) {
        return new RedisReplayGuard(
                redis,
                BridgeTokenVerifierTest.jobTraceProperties(),
                clock);
    }
}
