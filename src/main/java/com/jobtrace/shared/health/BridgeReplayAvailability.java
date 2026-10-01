package com.jobtrace.shared.health;

/** Minimal readiness view of the shared bridge replay store. */
@FunctionalInterface
public interface BridgeReplayAvailability {

    boolean isAvailable();
}
