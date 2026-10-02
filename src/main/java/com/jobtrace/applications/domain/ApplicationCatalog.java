package com.jobtrace.applications.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public final class ApplicationCatalog {

    private ApplicationCatalog() {}

    public enum ApplicationStatus implements WireValue {
        SUBMITTED("submitted"), OFFER("offer"), REFUSED("refused");

        private final String value;

        ApplicationStatus(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }

        public static Optional<ApplicationStatus> fromWire(String value) {
            return find(values(), value);
        }
    }

    public enum ApplicationType implements WireValue {
        SUMMER_INTERNSHIP("summer_internship"),
        DAILY_INTERNSHIP("daily_internship"),
        SPRING_RECRUITMENT("spring_recruitment"),
        EARLY_CAMPUS_RECRUITMENT("early_campus_recruitment"),
        CAMPUS_RECRUITMENT("campus_recruitment"),
        SOCIAL_RECRUITMENT("social_recruitment");

        private final String value;

        ApplicationType(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }

        public static Optional<ApplicationType> fromWire(String value) {
            return find(values(), value);
        }
    }

    public enum RecruitmentStage implements WireValue {
        SCREENING("screening"),
        ASSESSMENT("assessment"),
        WRITTEN_TEST("written_test"),
        INTERVIEW_1("interview_1"),
        INTERVIEW_2("interview_2"),
        INTERVIEW_3("interview_3"),
        HR_INTERVIEW("hr_interview"),
        FINAL_INTERVIEW("final_interview");

        private final String value;

        RecruitmentStage(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }

        public static Optional<RecruitmentStage> fromWire(String value) {
            return find(values(), value);
        }
    }

    public enum ApplicationSort implements WireValue {
        COMPANY("company"), POSITION("position"), APPLIED_DATE("appliedDate"), LATEST_DATE("latestDate");

        private final String value;

        ApplicationSort(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static Optional<ApplicationSort> fromWire(String value) {
            return find(values(), value);
        }
    }

    public enum SortDirection implements WireValue {
        ASC("asc"), DESC("desc");

        private final String value;

        SortDirection(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static Optional<SortDirection> fromWire(String value) {
            return find(values(), value);
        }
    }

    private interface WireValue {
        String value();
    }

    private static <T extends Enum<T> & WireValue> Optional<T> find(T[] values, String wireValue) {
        if (wireValue == null) {
            return Optional.empty();
        }
        return Arrays.stream(values)
                .filter(value -> value.value().equals(wireValue))
                .findFirst();
    }
}
