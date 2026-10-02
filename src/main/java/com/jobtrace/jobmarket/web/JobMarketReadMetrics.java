package com.jobtrace.jobmarket.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded marketplace read labels; never includes identity, filters, IDs, or URLs. */
@Component
public class JobMarketReadMetrics {

    private static final Set<String> OPERATIONS = Set.of("list", "detail");
    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "not_found",
            "dependency_failure", "failure");
    private final MeterRegistry registry;

    public JobMarketReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String operation, String outcome, Duration elapsed) {
        if (!OPERATIONS.contains(operation) || !OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized job-market metric label");
        }
        registry.counter("jobtrace.jobmarket.read.requests",
                "operation", operation, "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofMillis(500)) > 0) {
            registry.counter("jobtrace.jobmarket.read.budget_breaches",
                    "operation", operation).increment();
        }
        Timer.builder("jobtrace.jobmarket.read.duration")
                .tag("operation", operation).tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
