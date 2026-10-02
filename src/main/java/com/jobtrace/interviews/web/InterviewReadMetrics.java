package com.jobtrace.interviews.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded operation/outcome labels only; no owner, content, query, or token tags. */
@Component
public class InterviewReadMetrics {

    private static final Set<String> OPERATIONS = Set.of("list", "detail", "application_dialog");
    private static final Set<String> OUTCOMES = Set.of("success", "invalid", "denied_identity",
            "not_found", "dependency_failure", "failure");

    private final MeterRegistry registry;

    public InterviewReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String operation, String outcome, Duration elapsed) {
        if (!OPERATIONS.contains(operation) || !OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized interview metric label");
        }
        registry.counter("jobtrace.interviews.read.requests",
                "operation", operation, "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofMillis(500)) > 0) {
            registry.counter("jobtrace.interviews.read.budget_breaches",
                    "operation", operation).increment();
        }
        Timer.builder("jobtrace.interviews.read.duration")
                .tag("operation", operation).tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
