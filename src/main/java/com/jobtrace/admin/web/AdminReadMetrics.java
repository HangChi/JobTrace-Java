package com.jobtrace.admin.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded administration labels; identifiers, filters, reasons, and content are prohibited. */
@Component
public class AdminReadMetrics {

    private static final Set<String> OPERATIONS = Set.of("summary", "users", "user_detail", "audit");
    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "not_found",
            "conflict", "dependency_failure", "failure");
    private final MeterRegistry registry;

    public AdminReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String operation, String outcome, Duration elapsed) {
        if (!OPERATIONS.contains(operation) || !OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized administration metric label");
        }
        registry.counter("jobtrace.admin.read.requests",
                "operation", operation, "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofMillis(500)) > 0) {
            registry.counter("jobtrace.admin.read.budget_breaches",
                    "operation", operation).increment();
        }
        Timer.builder("jobtrace.admin.read.duration")
                .tag("operation", operation).tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }

    public void recordAccessChange(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized administration metric label");
        }
        registry.counter("jobtrace.admin.access_change.requests", "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.admin.access_change.budget_breaches").increment();
        }
        Timer.builder("jobtrace.admin.access_change.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
