package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Records bounded outcomes only; addresses and codes are never metric labels. */
@Component
public class AccountEmailCodeMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "conflict", "rate_limited",
            "invalid_recipient", "delivery_failure", "unavailable", "invalid_state",
            "dependency_failure", "failure");

    private final MeterRegistry registry;

    public AccountEmailCodeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized email code outcome");
        }
        registry.counter("jobtrace.identity_access.email.code.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.email.code.budget_breaches")
                    .increment();
        }
        Timer.builder("jobtrace.identity_access.email.code.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
