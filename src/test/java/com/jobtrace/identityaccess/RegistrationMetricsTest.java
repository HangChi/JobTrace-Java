package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.web.RegistrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RegistrationMetricsTest {

    @Test
    void recordsOnlyBoundedOutcomesAndBudgetBreaches() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RegistrationMetrics(registry);
        metrics.record("success", Duration.ofMillis(50));
        metrics.record("unavailable", Duration.ofSeconds(2));
        assertThat(registry.get("jobtrace.identity_access.registration.requests")
                .tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("jobtrace.identity_access.registration.budget_breaches")
                .counter().count()).isEqualTo(1);
        assertThatThrownBy(() -> metrics.record("email@example.test", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
