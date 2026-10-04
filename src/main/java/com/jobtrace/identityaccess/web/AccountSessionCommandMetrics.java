package com.jobtrace.identityaccess.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded write labels; owner, session, assertion, agent, and body data are prohibited. */
@Component
public class AccountSessionCommandMetrics {

    private static final Set<String> OUTCOMES = Set.of(
            "success", "denied_identity", "invalid", "not_found",
            "dependency_failure", "failure");

    private final MeterRegistry registry;

    public AccountSessionCommandMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration elapsed) {
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unrecognized session-command metric label");
        }
        registry.counter("jobtrace.identity_access.sessions.revoke.requests",
                "outcome", outcome).increment();
        if (elapsed.compareTo(Duration.ofSeconds(1)) > 0) {
            registry.counter(
                    "jobtrace.identity_access.sessions.revoke.budget_breaches").increment();
        }
        Timer.builder("jobtrace.identity_access.sessions.revoke.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram().register(registry).record(elapsed);
    }
}
