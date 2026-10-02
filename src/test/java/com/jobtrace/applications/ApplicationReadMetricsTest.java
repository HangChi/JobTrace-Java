package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.web.ApplicationReadMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApplicationReadMetricsTest {

    @Test
    void recordsBoundedOutcomeAndBudgetBreachWithoutIdentityLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ApplicationReadMetrics(registry);

        metrics.record("list", "success", Duration.ofMillis(20));
        metrics.record("detail", "dependency_failure", Duration.ofMillis(501));

        assertThat(registry.find("jobtrace.applications.read.requests")
                .tags("operation", "list", "outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.find("jobtrace.applications.read.budget_breaches")
                .tag("operation", "detail").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .allMatch(key -> Set.of("operation", "outcome").contains(key)));
    }
}
