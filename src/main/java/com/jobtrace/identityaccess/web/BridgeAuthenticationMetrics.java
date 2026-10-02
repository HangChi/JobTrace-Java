package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Bounded, identity-free telemetry for bridge authentication. */
@Component
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public final class BridgeAuthenticationMetrics {

    private final MeterRegistry registry;

    public BridgeAuthenticationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void success(Duration elapsed) {
        registry.counter("jobtrace.auth.bridge.requests", "result", "success")
                .increment();
        timer("success").record(elapsed);
    }

    public void failure(BridgeAuthenticationFailure failure, Duration elapsed) {
        String reason = failure.name().toLowerCase(Locale.ROOT);
        registry.counter(
                        "jobtrace.auth.bridge.requests",
                        "result",
                        "failure",
                        "reason",
                        reason)
                .increment();
        timer("failure").record(elapsed);
    }

    private Timer timer(String result) {
        return Timer.builder("jobtrace.auth.bridge.duration")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry);
    }
}
