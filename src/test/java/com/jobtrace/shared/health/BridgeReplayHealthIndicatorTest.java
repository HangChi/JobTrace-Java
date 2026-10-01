package com.jobtrace.shared.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BridgeReplayHealthIndicatorTest {

    @Test
    void reportsReplayStoreAvailabilityWithoutSensitiveDetails() {
        var up = new BridgeReplayHealthIndicator(() -> true).health();
        var down = new BridgeReplayHealthIndicator(() -> false).health();

        assertThat(up.getStatus().getCode()).isEqualTo("UP");
        assertThat(down.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(down.getDetails())
                .containsEntry("reason", "replay_store_unavailable")
                .hasSize(1);
    }
}
