package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded registration outcomes without usernames, addresses, codes or tokens. */
@Component
public class RegistrationMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "invalid", "invalid_code", "conflict", "rate_limited",
            "csrf_rejected", "challenge_required", "challenge_failed",
            "unavailable", "failure");

    private final MeterRegistry registry;

    public RegistrationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized registration outcome");
        }
        registry.counter("jobtrace.identity_access.registration.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.registration.budget_breaches")
                    .increment();
        }
        Timer.builder("jobtrace.identity_access.registration.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
