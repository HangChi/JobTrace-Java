package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Fixed outcome labels without cookies, tokens or user identifiers. */
@Component
public class LogoutMetrics {
    private static final Set<String> OUTCOMES = Set.of("deleted", "no_session",
            "delete_failure", "csrf_rejected", "unavailable", "failure");
    private final MeterRegistry registry;

    public LogoutMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unknown logout outcome");
        }
        registry.counter("jobtrace.identity_access.logout.requests", "outcome", outcome)
                .increment();
        Timer.builder("jobtrace.identity_access.logout.duration").tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.logout.budget_breaches").increment();
        }
    }
}
