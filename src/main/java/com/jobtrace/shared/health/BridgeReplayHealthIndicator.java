package com.jobtrace.shared.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Makes replay-store failure visible to readiness when bridge traffic is enabled. */
@Component("bridgeReplay")
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public final class BridgeReplayHealthIndicator implements HealthIndicator {

    private final BridgeReplayAvailability replayStore;

    public BridgeReplayHealthIndicator(BridgeReplayAvailability replayStore) {
        this.replayStore = replayStore;
    }

    @Override
    public Health health() {
        return replayStore.isAvailable()
                ? Health.up().build()
                : Health.down().withDetail("reason", "replay_store_unavailable").build();
    }
}
