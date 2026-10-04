package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LoginMetrics {
    private static final Set<String> OUTCOMES = Set.of("success", "invalid", "denied",
            "rate_limited", "csrf_rejected", "challenge_required", "challenge_failed",
            "unavailable", "failure");
    private final MeterRegistry registry;

    public LoginMetrics(MeterRegistry registry) { this.registry = registry; }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unknown login outcome");
        }
        registry.counter("jobtrace.identity_access.login.requests", "outcome", outcome).increment();
        Timer.builder("jobtrace.identity_access.login.duration").tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.login.budget_breaches").increment();
        }
    }
}
