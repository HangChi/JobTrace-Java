package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded labels only; profile values and identity data are prohibited. */
@Component
public class AccountProfileMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "not_found",
            "dependency_failure", "failure");

    private final MeterRegistry registry;

    public AccountProfileMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized profile metric label");
        }
        registry.counter("jobtrace.identity_access.profile.update.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter("jobtrace.identity_access.profile.update.budget_breaches")
                    .increment();
        }
        Timer.builder("jobtrace.identity_access.profile.update.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
