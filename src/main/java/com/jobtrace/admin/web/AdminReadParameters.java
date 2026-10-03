package com.jobtrace.admin.web;

import com.jobtrace.admin.domain.AdminQuery;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.util.MultiValueMap;

public final class AdminReadParameters {

    private AdminReadParameters() {}

    public static AdminQuery.Users users(MultiValueMap<String, String> values) {
        return new AdminQuery.Users(text(values, "q"),
                enumeration(values, "role", AdminQuery.UserRole.ALL, AdminQuery.UserRole.class),
                enumeration(values, "status", AdminQuery.UserStatus.ALL,
                        AdminQuery.UserStatus.class),
                date(values, "registeredFrom"), date(values, "registeredTo"),
                integer(values, "page", 1), boundedLimit(values));
    }

    public static AdminQuery.Audit audit(MultiValueMap<String, String> values) {
        return new AdminQuery.Audit(text(values, "actor"), text(values, "target"),
                enumeration(values, "eventType", AdminQuery.EventType.ALL,
                        AdminQuery.EventType.class),
                enumeration(values, "outcome", AdminQuery.Outcome.ALL, AdminQuery.Outcome.class),
                date(values, "occurredFrom"), date(values, "occurredTo"),
                integer(values, "page", 1), boundedLimit(values));
    }

    public static AdminQuery.Content content(MultiValueMap<String, String> values) {
        return new AdminQuery.Content(integer(values, "applicationsPage", 1),
                integer(values, "interviewsPage", 1));
    }

    private static int boundedLimit(MultiValueMap<String, String> values) {
        int limit = integer(values, "limit", 50);
        if (limit > 100) {
            throw new IllegalArgumentException("limit exceeds 100");
        }
        return limit;
    }

    private static String text(MultiValueMap<String, String> values, String name) {
        String value = values.getFirst(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("text is too long");
        }
        return normalized;
    }

    private static LocalDate date(MultiValueMap<String, String> values, String name) {
        String value = text(values, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid date", exception);
        }
    }

    private static int integer(
            MultiValueMap<String, String> values, String name, int fallback) {
        String value = text(values, name);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("integer must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid integer", exception);
        }
    }

    private static <T extends Enum<T>> T enumeration(
            MultiValueMap<String, String> values, String name, T fallback, Class<T> type) {
        String value = text(values, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid enum", exception);
        }
    }
}
