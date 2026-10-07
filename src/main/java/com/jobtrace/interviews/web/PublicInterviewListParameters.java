package com.jobtrace.interviews.web;

import com.jobtrace.interviews.domain.PublicInterviewCriteria;
import com.jobtrace.interviews.domain.PublicInterviewCriteria.Sort;
import org.springframework.util.MultiValueMap;

public final class PublicInterviewListParameters {

    private PublicInterviewListParameters() {}

    public static PublicInterviewCriteria from(MultiValueMap<String, String> values) {
        String rawSort = first(values, "sort", "latest");
        Sort sort = switch (rawSort) {
            case "latest" -> Sort.LATEST;
            case "hot" -> Sort.HOT;
            default -> throw new IllegalArgumentException("Invalid public interview sort");
        };
        int limit;
        try {
            limit = Integer.parseInt(first(values, "limit", "20"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid public interview limit", exception);
        }
        String cursor = values.getFirst("cursor");
        return new PublicInterviewCriteria(first(values, "q", "").trim(),
                first(values, "city", "").trim(), first(values, "position", "").trim(),
                sort, cursor == null || cursor.isEmpty() ? null : cursor, limit);
    }

    private static String first(MultiValueMap<String, String> values, String key, String fallback) {
        String value = values.getFirst(key);
        return value == null ? fallback : value;
    }
}
