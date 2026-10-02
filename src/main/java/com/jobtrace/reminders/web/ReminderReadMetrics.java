package com.jobtrace.reminders.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded reminder read labels; no owner, content, address, or selection tags. */
@Component
public class ReminderReadMetrics {

    private static final Set<String> OPERATIONS = Set.of("summary", "preferences");
    private static final Set<String> OUTCOMES = Set.of("success", "denied_identity",
            "dependency_failure", "failure");

    private final MeterRegistry registry;

    public ReminderReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String operation, String outcome, Duration elapsed) {
        if (!OPERATIONS.contains(operation) || !OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized reminder metric label");
        }
        registry.counter("jobtrace.reminders.read.requests",
                "operation", operation, "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofMillis(500)) > 0) {
            registry.counter("jobtrace.reminders.read.budget_breaches",
                    "operation", operation).increment();
        }
        Timer.builder("jobtrace.reminders.read.duration")
                .tag("operation", operation).tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
