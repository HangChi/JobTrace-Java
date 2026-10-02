package com.jobtrace.reminders.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/** Wire values retained from the existing scheduled-reminder read contract. */
public final class ReminderCatalog {

    private ReminderCatalog() {}

    public enum Status implements WireValue {
        PENDING("pending"), DUE("due"), COMPLETED("completed"), CANCELLED("cancelled");
        private final String value;
        Status(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Status> fromWire(String value) { return find(values(), value); }
    }

    public enum AttemptStatus implements WireValue {
        CLAIMED("claimed"), SENT("sent"), FAILED("failed"), SKIPPED("skipped");
        private final String value;
        AttemptStatus(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<AttemptStatus> fromWire(String value) { return find(values(), value); }
    }

    private interface WireValue { String value(); }

    private static <T extends Enum<T> & WireValue> Optional<T> find(T[] values, String wire) {
        return Arrays.stream(values).filter(value -> value.value().equals(wire)).findFirst();
    }
}
