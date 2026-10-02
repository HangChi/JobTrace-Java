package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.web.InterviewReadMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterviewReadMetricsTest {

    @Test
    void allOperationsHaveBoundedOutcomeAndLatencyLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new InterviewReadMetrics(registry);
        for (String operation : new String[] {"list", "detail", "application_dialog"}) {
            for (String outcome : new String[] {"success", "invalid", "denied_identity",
                    "not_found", "dependency_failure", "failure"}) {
                metrics.record(operation, outcome, Duration.ofMillis(501));
            }
            assertThat(registry.find("jobtrace.interviews.read.budget_breaches")
                    .tag("operation", operation).counter().count()).isEqualTo(6);
        }
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                        .allMatch(key -> Set.of("operation", "outcome").contains(key)));
        assertThatThrownBy(() -> metrics.record("owner-a", "success", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
