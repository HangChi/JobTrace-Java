package com.jobtrace.admin.domain;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(List<T> items, int total, int page, int limit, int totalPages) {

    public PageResult {
        items = List.copyOf(Objects.requireNonNull(items));
        if (total < 0 || page < 1 || limit < 1 || totalPages < 0) {
            throw new IllegalArgumentException("invalid page metadata");
        }
    }

    public static <T> PageResult<T> of(List<T> items, int total, int page, int limit) {
        int pages = total == 0 ? 0 : (total + limit - 1) / limit;
        return new PageResult<>(items, total, page, limit, pages);
    }
}
