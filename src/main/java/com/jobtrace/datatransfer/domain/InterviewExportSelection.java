package com.jobtrace.datatransfer.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Ordered distinct review IDs; the trusted owner is supplied separately. */
public record InterviewExportSelection(List<UUID> ids) {

    public InterviewExportSelection {
        ids = List.copyOf(ids);
        if (ids.isEmpty() || ids.size() > 100) {
            throw new IllegalArgumentException("Invalid interview export selection");
        }
    }

    public static InterviewExportSelection from(List<String> suppliedIds) {
        if (suppliedIds.isEmpty() || suppliedIds.size() > 100) {
            throw new IllegalArgumentException("Invalid interview export selection");
        }
        var distinct = new LinkedHashSet<UUID>();
        suppliedIds.stream().map(UUID::fromString).forEach(distinct::add);
        return new InterviewExportSelection(List.copyOf(distinct));
    }
}
