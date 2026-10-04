package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded issuance diagnostics without addresses, codes, or request secrets. */
@Component
public class RegistrationEmailCodeMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "csrf_rejected", "invalid", "conflict", "rate_limited",
            "invalid_recipient", "delivery_failure", "unavailable",
            "dependency_failure", "failure");

    private final MeterRegistry registry;

    public RegistrationEmailCodeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized registration-code outcome");
        }
        registry.counter("jobtrace.identity_access.registration.code.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.registration.code.budget_breaches")
                    .increment();
        }
        Timer.builder("jobtrace.identity_access.registration.code.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
