package com.jobtrace.analytics.domain;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Resolves the legacy analytics period and comparison window in business dates. */
public record AnalyticsReportRange(
        String period,
        LocalDate from,
        LocalDate to,
        LocalDate comparisonFrom,
        LocalDate comparisonTo,
        String granularity,
        ApplicationType type,
        String city,
        boolean hasCityFilter) {

    public static AnalyticsReportRange from(Map<String, String> parameters, LocalDate today) {
        String requested = parameters.get("period");
        String period = switch (requested == null ? "90d" : requested) {
            case "30d", "90d", "180d", "ytd", "all", "custom" -> requested == null ? "90d" : requested;
            default -> "90d";
        };
        ApplicationType type = ApplicationType.fromWire(parameters.get("type")).orElse(null);
        boolean hasCityFilter = parameters.containsKey("city")
                && !"__all__".equals(parameters.get("city"));
        String city = hasCityFilter ? truncate(parameters.get("city"), 100) : null;

        LocalDate from = null;
        LocalDate to = today;
        switch (period) {
            case "30d" -> from = today.minusDays(29);
            case "90d" -> from = today.minusDays(89);
            case "180d" -> from = today.minusDays(179);
            case "ytd" -> from = today.withDayOfYear(1);
            case "all" -> to = null;
            case "custom" -> {
                from = validDate(parameters.get("from"));
                to = validDate(parameters.get("to"));
                if (from == null || to == null) {
                    throw invalid("请选择完整且有效的开始与结束日期。");
                }
                if (from.isAfter(to)) {
                    throw invalid("开始日期不能晚于结束日期。");
                }
                if (to.isAfter(today)) {
                    throw invalid("结束日期不能晚于今天。");
                }
            }
            default -> throw new IllegalStateException("unhandled period");
        }

        LocalDate comparisonFrom = null;
        LocalDate comparisonTo = null;
        if (from != null && to != null) {
            if ("ytd".equals(period)) {
                comparisonFrom = today.minusYears(1).withDayOfYear(1);
                comparisonTo = to.minusYears(1);
            } else {
                long length = ChronoUnit.DAYS.between(from, to) + 1;
                comparisonTo = from.minusDays(1);
                comparisonFrom = comparisonTo.minusDays(length - 1);
            }
        }
        long span = from == null || to == null ? Long.MAX_VALUE
                : ChronoUnit.DAYS.between(from, to) + 1;
        return new AnalyticsReportRange(period, from, to, comparisonFrom, comparisonTo,
                span <= 180 ? "week" : "month", type, city, hasCityFilter);
    }

    private static LocalDate validDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String date = truncate(raw, 10);
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), maxLength));
    }

    private static InvalidAnalyticsRangeException invalid(String message) {
        return new InvalidAnalyticsRangeException(message);
    }
}
