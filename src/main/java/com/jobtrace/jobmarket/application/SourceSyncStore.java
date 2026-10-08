package com.jobtrace.jobmarket.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceSyncStore {
    Optional<Lease> claimOne(UUID sourceId, String workerId, String requestId, Instant now);

    List<Lease> claimDue(int limit, String workerId, String requestId, Instant now);

    boolean complete(Lease lease, Instant now, Completion completion);

    RunPage history(UUID sourceId, int page, int limit);

    record Lease(UUID sourceId, UUID runId, String workerId, String adapter,
            String externalKey, String baseUrl, List<String> allowedHosts,
            List<String> countryCodes, int consecutiveFailures) {}

    record Completion(String status, int discovered, int created, int updated,
            int stale, int closed, int rejected, String errorCode,
            String errorSummary) {
        public Completion {
            if (!List.of("succeeded", "partial", "failed").contains(status)
                    || discovered < 0 || created < 0 || updated < 0 || stale < 0
                    || closed < 0 || rejected < 0
                    || errorCode != null && errorCode.length() > 100
                    || errorSummary != null && errorSummary.length() > 500) {
                throw new IllegalArgumentException("无效的同步完成结果。");
            }
        }

        public static Completion success() {
            return new Completion("succeeded", 0, 0, 0, 0, 0, 0, null, null);
        }

        public static Completion failure(String code, String summary) {
            return new Completion("failed", 0, 0, 0, 0, 0, 0, code, summary);
        }
    }

    record Run(UUID id, UUID sourceId, String trigger, String status,
            Instant startedAt, Instant finishedAt, Counts counts,
            String errorCode, String errorSummary) {}

    record Counts(int discovered, int created, int updated, int stale,
            int closed, int rejected) {}

    record RunPage(List<Run> items, int page, int limit, int total) {}
}
