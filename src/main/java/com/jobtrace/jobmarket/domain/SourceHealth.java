package com.jobtrace.jobmarket.domain;

import java.time.Instant;
import java.util.UUID;

public record SourceHealth(
        UUID id,
        Company company,
        String adapter,
        String baseUrl,
        String status,
        Instant nextSyncAt,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        int consecutiveFailures,
        LatestRun latestRun) {

    public record Company(UUID id, String name, String type, String industry) {
    }

    public record LatestRun(
            UUID id,
            UUID sourceId,
            String trigger,
            String status,
            Instant startedAt,
            Instant finishedAt,
            Counts counts,
            String errorCode,
            String errorSummary) {
    }

    public record Counts(int discovered, int created, int updated,
                         int stale, int closed, int rejected) {
    }
}
