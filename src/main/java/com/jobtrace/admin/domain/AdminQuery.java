package com.jobtrace.admin.domain;

import java.time.LocalDate;

public final class AdminQuery {

    private AdminQuery() {}

    public enum UserRole { ALL, USER, ADMIN }

    public enum UserStatus { ALL, ACTIVE, DISABLED }

    public enum EventType {
        ALL, PROMOTE_ADMIN, DEMOTE_ADMIN, DISABLE_USER, ENABLE_USER
    }

    public enum Outcome { ALL, SUCCEEDED, DENIED, CONFLICT, FAILED }

    public record Users(
            String q,
            UserRole role,
            UserStatus status,
            LocalDate registeredFrom,
            LocalDate registeredTo,
            int page,
            int limit) {

        public Users {
            if (role == null || status == null || page < 1 || limit < 1 || limit > 100) {
                throw new IllegalArgumentException("invalid user query");
            }
            validateRange(registeredFrom, registeredTo);
        }
    }

    public record Audit(
            String actor,
            String target,
            EventType eventType,
            Outcome outcome,
            LocalDate occurredFrom,
            LocalDate occurredTo,
            int page,
            int limit) {

        public Audit {
            if (eventType == null || outcome == null || page < 1 || limit < 1 || limit > 100) {
                throw new IllegalArgumentException("invalid audit query");
            }
            validateRange(occurredFrom, occurredTo);
        }
    }

    public record Content(int applicationsPage, int interviewsPage) {
        public Content {
            if (applicationsPage < 1 || interviewsPage < 1) {
                throw new IllegalArgumentException("invalid content page");
            }
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("date range is reversed");
        }
    }
}
