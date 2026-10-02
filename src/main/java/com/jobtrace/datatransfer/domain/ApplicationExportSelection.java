package com.jobtrace.datatransfer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable owner-independent application export criteria. */
public record ApplicationExportSelection(
        Scope scope, Format format, List<UUID> ids, String query,
        List<String> statuses, List<String> types, List<String> stages,
        List<String> cities, LocalDate appliedFrom, LocalDate appliedTo) {

    public enum Scope { ALL, FILTERED, SELECTED }
    public enum Format { CSV, XLSX }

    public ApplicationExportSelection {
        ids = List.copyOf(ids);
        statuses = List.copyOf(statuses);
        types = List.copyOf(types);
        stages = List.copyOf(stages);
        cities = List.copyOf(cities);
        if (ids.size() > 100 || scope == Scope.SELECTED && ids.isEmpty()) {
            throw new IllegalArgumentException("Invalid application export selection");
        }
    }

    public static ApplicationExportSelection from(
            String scope, String format, List<String> ids, String query,
            List<String> statuses, List<String> types, List<String> stages,
            List<String> cities, String appliedFrom, String appliedTo) {
        if (ids.size() > 100) {
            throw new IllegalArgumentException("Too many selected applications");
        }
        Scope parsedScope = switch (scope == null ? "" : scope) {
            case "all" -> Scope.ALL;
            case "selected" -> Scope.SELECTED;
            default -> Scope.FILTERED;
        };
        Format parsedFormat = "csv".equals(format) ? Format.CSV : Format.XLSX;
        return new ApplicationExportSelection(parsedScope, parsedFormat,
                ids.stream().map(UUID::fromString).toList(),
                query == null || query.isEmpty() ? null
                        : query.substring(0, Math.min(query.length(), 200)),
                statuses, types, stages, cities,
                appliedFrom == null || appliedFrom.isBlank() ? null : LocalDate.parse(appliedFrom),
                appliedTo == null || appliedTo.isBlank() ? null : LocalDate.parse(appliedTo));
    }
}
