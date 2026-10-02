package com.jobtrace.datatransfer.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Download metrics with only fixed, non-sensitive label values. */
@Component
public class ExportMetrics {

    private final MeterRegistry registry;

    public ExportMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String operation, String outcome, Duration elapsed) {
        if (!("applications".equals(operation) || "interviews".equals(operation))) {
            throw new IllegalArgumentException("Unknown export operation");
        }
        if (!("success".equals(outcome) || "not_found".equals(outcome)
                || "invalid".equals(outcome) || "denied_identity".equals(outcome)
                || "dependency_failure".equals(outcome) || "failure".equals(outcome))) {
            throw new IllegalArgumentException("Unknown export outcome");
        }
        registry.counter("jobtrace.export.requests", "operation", operation,
                "outcome", outcome).increment();
        Timer.builder("jobtrace.export.duration").tag("operation", operation)
                .tag("outcome", outcome).register(registry).record(elapsed);
    }
}
