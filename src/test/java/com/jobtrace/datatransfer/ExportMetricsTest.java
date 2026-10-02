package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.datatransfer.web.ExportMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExportMetricsTest {

    @Test
    void onlyBoundedOperationAndOutcomeLabelsAreAccepted() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ExportMetrics(registry);
        metrics.record("applications", "success", Duration.ofMillis(2));
        metrics.record("interviews", "not_found", Duration.ofMillis(3));
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getKey()).isIn("operation", "outcome")));
        assertThatThrownBy(() -> metrics.record("owner-a", "success", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.record("applications", "owner-a", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
