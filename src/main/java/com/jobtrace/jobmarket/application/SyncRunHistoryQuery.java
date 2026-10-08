package com.jobtrace.jobmarket.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only view of runs owned and written by the legacy ingestion service. */
public interface SyncRunHistoryQuery {
    RunPage history(UUID sourceId, int page, int limit);

    record Run(UUID id, UUID sourceId, String trigger, String status,
            Instant startedAt, Instant finishedAt, Counts counts,
            String errorCode, String errorSummary) {}

    record Counts(int discovered, int created, int updated, int stale,
            int closed, int rejected) {}

    record RunPage(List<Run> items, int page, int limit, int total) {}
}
