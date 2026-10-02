package com.jobtrace.interviews.domain;

import java.util.List;
import java.util.Objects;

public record InterviewPage(List<InterviewSummary> items, String nextCursor, int total, int limit) {
    public InterviewPage {
        items = List.copyOf(Objects.requireNonNull(items));
        if (total < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid page metadata");
        }
    }
}
