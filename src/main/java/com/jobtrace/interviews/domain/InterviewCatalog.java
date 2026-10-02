package com.jobtrace.interviews.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/** Wire values frozen from the existing private interview API. */
public final class InterviewCatalog {

    private InterviewCatalog() {}

    public enum Stage implements WireValue {
        ASSESSMENT("assessment"), INTERVIEW_1("interview_1"), INTERVIEW_2("interview_2"),
        INTERVIEW_3("interview_3"), HR_INTERVIEW("hr_interview"), FINAL_INTERVIEW("final_interview");
        private final String value;
        Stage(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Stage> fromWire(String value) { return find(values(), value); }
    }

    public enum Status implements WireValue {
        DRAFT("draft"), PENDING_REVIEW("pending_review"), COMPLETED("completed");
        private final String value;
        Status(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Status> fromWire(String value) { return find(values(), value); }
    }

    public enum Result implements WireValue {
        PENDING("pending"), PASSED("passed"), FAILED("failed");
        private final String value;
        Result(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Result> fromWire(String value) { return find(values(), value); }
    }

    public enum Publication implements WireValue {
        PRIVATE("private"), ANONYMOUS("anonymous"), ATTRIBUTED("attributed");
        private final String value;
        Publication(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Publication> fromWire(String value) { return find(values(), value); }
    }

    public enum Visibility implements WireValue {
        PRIVATE("private"), PUBLIC("public");
        private final String value;
        Visibility(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Visibility> fromWire(String value) { return find(values(), value); }
    }

    public enum AuthorMode implements WireValue {
        ANONYMOUS("anonymous"), ATTRIBUTED("attributed");
        private final String value;
        AuthorMode(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<AuthorMode> fromWire(String value) { return find(values(), value); }
    }

    public enum Format implements WireValue {
        ONLINE("online"), OFFLINE("offline"), PHONE("phone");
        private final String value;
        Format(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<Format> fromWire(String value) { return find(values(), value); }
    }

    public enum QuestionCategory implements WireValue {
        TECHNICAL("technical"), PROJECT("project"), BEHAVIORAL("behavioral"),
        SYSTEM_DESIGN("system_design"), OTHER("other");
        private final String value;
        QuestionCategory(String value) { this.value = value; }
        @Override @JsonValue public String value() { return value; }
        public static Optional<QuestionCategory> fromWire(String value) { return find(values(), value); }
    }

    private interface WireValue { String value(); }

    private static <T extends Enum<T> & WireValue> Optional<T> find(T[] values, String wire) {
        return Arrays.stream(values).filter(value -> value.value().equals(wire)).findFirst();
    }
}
