package com.jobtrace.applications.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Only bounded operation and outcome labels are retained; identifiers and queries are excluded. */
@Component
public class ApplicationReadMetrics {

    private final MeterRegistry registry;

    public ApplicationReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String operation, String outcome, Duration elapsed) {
        registry.counter("jobtrace.applications.read.requests",
                "operation", operation, "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofMillis(500)) > 0) {
            registry.counter("jobtrace.applications.read.budget_breaches",
                    "operation", operation).increment();
        }
        Timer.builder("jobtrace.applications.read.duration")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry)
                .record(elapsed);
    }
}
