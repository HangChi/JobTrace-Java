package com.jobtrace.jobmarket.web;

import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.util.MultiValueMap;

public final class MarketplaceQueryParameters {

    private MarketplaceQueryParameters() {}

    public static MarketplaceQuery from(MultiValueMap<String, String> parameters) {
        return new MarketplaceQuery(
                text(parameters.getFirst("q")),
                text(parameters.getFirst("company")),
                text(parameters.getFirst("location")),
                status(parameters.getFirst("status")),
                date(parameters.getFirst("postedFrom")),
                bool(parameters.getFirst("favorite")),
                integer(parameters.getFirst("page"), 1),
                integer(parameters.getFirst("limit"), 20));
    }

    private static String text(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("text filter is too long");
        }
        return normalized;
    }

    private static PostStatus status(String value) {
        String normalized = text(value);
        return normalized == null ? null : PostStatus.fromWire(normalized);
    }

    private static LocalDate date(String value) {
        String normalized = text(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid date", exception);
        }
    }

    private static Boolean bool(String value) {
        String normalized = text(value);
        if (normalized == null) {
            return null;
        }
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("invalid boolean");
    }

    private static int integer(String value, int fallback) {
        String normalized = text(value);
        if (normalized == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid integer", exception);
        }
    }
}
