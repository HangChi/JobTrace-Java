package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded session-read labels; owner, session, agent, and assertion data are prohibited. */
@Component
public class AccountSessionReadMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "dependency_failure", "failure");

    private final MeterRegistry registry;

    public AccountSessionReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized session-read metric label");
        }
        registry.counter("jobtrace.identity_access.sessions.read.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofMillis(500)) > 0) {
            registry.counter("jobtrace.identity_access.sessions.read.budget_breaches").increment();
        }
        Timer.builder("jobtrace.identity_access.sessions.read.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
