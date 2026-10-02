package com.jobtrace.applications.domain;

import java.util.List;
import java.util.Objects;

public record ApplicationPage(
        List<ApplicationSummary> items,
        String nextCursor,
        int total,
        int page,
        int limit) {

    public ApplicationPage {
        items = List.copyOf(Objects.requireNonNull(items));
        if (total < 0 || page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid application page metadata");
        }
    }
}
