package com.jobtrace.applications.domain;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationSort;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import com.jobtrace.applications.domain.ApplicationCatalog.SortDirection;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record ApplicationListCriteria(
        String query,
        List<ApplicationStatus> statuses,
        List<ApplicationType> types,
        List<RecruitmentStage> stages,
        List<String> cities,
        LocalDate appliedFrom,
        LocalDate appliedTo,
        ApplicationSort sort,
        boolean defaultOrder,
        SortDirection direction,
        String cursor,
        int page,
        int limit) {

    public ApplicationListCriteria {
        statuses = List.copyOf(statuses);
        types = List.copyOf(types);
        stages = List.copyOf(stages);
        cities = List.copyOf(cities);
    }

    public static ApplicationListCriteria from(
            String query,
            List<String> statuses,
            List<String> types,
            List<String> stages,
            List<String> cities,
            String appliedFrom,
            String appliedTo,
            String sort,
            String direction,
            String cursor,
            String page,
            String limit) {
        String normalizedQuery = bounded(trimmed(query), 200);
        ApplicationSort normalizedSort = ApplicationSort.fromWire(sort)
                .orElse(ApplicationSort.LATEST_DATE);
        boolean defaultOrder = ApplicationSort.fromWire(sort).isEmpty();
        SortDirection defaultDirection = normalizedSort == ApplicationSort.COMPANY
                        || normalizedSort == ApplicationSort.POSITION
                ? SortDirection.ASC
                : SortDirection.DESC;

        return new ApplicationListCriteria(
                normalizedQuery,
                known(statuses, ApplicationStatus::fromWire),
                known(types, ApplicationType::fromWire),
                known(stages, RecruitmentStage::fromWire),
                safe(cities).stream().map(value -> bounded(value, 100)).toList(),
                date(appliedFrom),
                date(appliedTo),
                normalizedSort,
                defaultOrder,
                SortDirection.fromWire(direction).orElse(defaultDirection),
                blankToNull(cursor),
                Math.max(1, integer(page, 1)),
                Math.min(100, Math.max(1, integer(limit, 50))));
    }

    private static <T> List<T> known(List<String> values, Function<String, Optional<T>> parser) {
        return safe(values).stream().map(parser).flatMap(Optional::stream).toList();
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String bounded(String value, int maximumLength) {
        String normalized = blankToNull(value);
        if (normalized == null || normalized.length() <= maximumLength) {
            return normalized;
        }
        return normalized.substring(0, maximumLength);
    }

    private static LocalDate date(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
