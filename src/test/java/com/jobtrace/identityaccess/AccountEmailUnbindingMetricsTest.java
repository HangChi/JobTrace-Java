package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.web.AccountEmailUnbindingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AccountEmailUnbindingMetricsTest {

    @Test
    void recordsOnlyBoundedOutcomesAndLatencyBudget() {
        var registry = new SimpleMeterRegistry();
        var metrics = new AccountEmailUnbindingMetrics(registry);
        metrics.record("success", Duration.ofMillis(3));
        metrics.record("rate_limited", Duration.ofSeconds(2));

        assertThat(registry.counter("jobtrace.identity_access.email.unbind.requests",
                "outcome", "success").count()).isEqualTo(1);
        assertThat(registry.counter("jobtrace.identity_access.email.unbind.budget_breaches")
                .count()).isEqualTo(1);
        assertThatThrownBy(() -> metrics.record("owner@example.test", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.getMeters()).noneMatch(meter -> meter.getId().getTags()
                .stream().anyMatch(tag -> tag.getValue().contains("@")));
    }
}
