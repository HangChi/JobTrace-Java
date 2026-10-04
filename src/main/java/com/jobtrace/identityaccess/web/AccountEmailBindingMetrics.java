package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Records only fixed outcomes and duration, without email or secret values. */
@Component
public class AccountEmailBindingMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "invalid_password",
            "invalid_code", "conflict", "rate_limited", "unavailable",
            "invalid_state", "dependency_failure", "failure");

    private final MeterRegistry registry;

    public AccountEmailBindingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized email binding outcome");
        }
        registry.counter("jobtrace.identity_access.email.bind.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.email.bind.budget_breaches")
                    .increment();
        }
        Timer.builder("jobtrace.identity_access.email.bind.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
