package com.jobtrace.analytics.application;

import com.jobtrace.analytics.domain.ProgressReminderNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProgressReminderResolution {

    private final Store store;

    public ProgressReminderResolution(Store store) {
        this.store = store;
    }

    public Result resolve(String ownerId, UUID stageOccurrenceId, Resolution resolution) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        Objects.requireNonNull(stageOccurrenceId);
        Objects.requireNonNull(resolution);
        if (!store.resolve(ownerId, stageOccurrenceId, resolution)) {
            throw new ProgressReminderNotFoundException();
        }
        return new Result(true, resolution);
    }

    public enum Resolution {
        COMPLETED("completed"), DISMISSED("dismissed");

        private final String wire;

        Resolution(String wire) {
            this.wire = wire;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String wire() {
            return wire;
        }

        public static Resolution fromWire(String value) {
            for (Resolution resolution : values()) {
                if (resolution.wire.equals(value)) {
                    return resolution;
                }
            }
            throw new IllegalArgumentException("resolution must be completed or dismissed");
        }
    }

    public record Result(boolean resolved, Resolution resolution) {}

    public interface Store {
        boolean resolve(String ownerId, UUID stageOccurrenceId, Resolution resolution);
    }
}
