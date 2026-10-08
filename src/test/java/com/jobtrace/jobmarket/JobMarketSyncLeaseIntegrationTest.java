package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.jobmarket.infrastructure.PostgresSourceSyncStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketSyncLeaseIntegrationTest extends JobMarketReadDatabaseTest {
    private static final UUID SOURCE = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Autowired
    private PostgresSourceSyncStore sync;

    @Test
    void onlyOneWorkerOwnsAnActiveLeaseAndCompletionIsFenced() {
        var first = sync.claimOne(SOURCE, "worker-a", "request-a", NOW);
        assertThat(first).isPresent();
        assertThat(sync.claimOne(SOURCE, "worker-b", "request-b", NOW)).isEmpty();
        assertThat(sync.complete(first.orElseThrow(), NOW.plusSeconds(10), true)).isTrue();
        assertThat(sync.complete(first.orElseThrow(), NOW.plusSeconds(11), true)).isFalse();
        assertThat(jdbc.queryForObject("select status from job_market_sync_runs "
                + "where id=?", String.class, first.orElseThrow().runId()))
                .isEqualTo("succeeded");
    }

    @Test
    void expiredLeaseFailsPriorRunAndRejectsItsStaleCompletion() {
        var first = sync.claimOne(SOURCE, "worker-a", "request-a", NOW).orElseThrow();
        jdbc.update("update job_market_sources set lease_until=? where id=?",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), SOURCE);
        var second = sync.claimOne(SOURCE, "worker-b", "request-b", NOW.plusSeconds(1))
                .orElseThrow();
        assertThat(second.runId()).isNotEqualTo(first.runId());
        assertThat(sync.complete(first, NOW.plusSeconds(2), true)).isFalse();
        assertThat(sync.complete(second, NOW.plusSeconds(3), false)).isTrue();
        assertThat(jdbc.queryForObject("select error_code from job_market_sync_runs "
                + "where id=?", String.class, first.runId())).isEqualTo("lease_expired");
        assertThat(sync.history(SOURCE, 1, 20).total()).isEqualTo(2);
    }

    @Test
    void dueClaimUsesScheduledTriggerAndSkipsBusySource() {
        jdbc.update("update job_market_sources set next_sync_at=? where id=?",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), SOURCE);
        var claims = sync.claimDue(10, "scheduler", "tick-1", NOW);
        assertThat(claims).extracting(lease -> lease.sourceId()).contains(SOURCE);
        assertThat(sync.claimDue(10, "scheduler-2", "tick-2", NOW))
                .extracting(lease -> lease.sourceId()).doesNotContain(SOURCE);
        assertThat(jdbc.queryForObject("select trigger from job_market_sync_runs "
                + "where id=?", String.class, claims.stream()
                .filter(lease -> SOURCE.equals(lease.sourceId()))
                .findFirst().orElseThrow().runId())).isEqualTo("scheduled");
    }

    @Test
    void failureSchedulesRetryAndHistoryRejectsInvalidPagination() {
        var lease = sync.claimOne(SOURCE, "worker", "request", NOW).orElseThrow();
        assertThat(sync.complete(lease, NOW.plusSeconds(5), false)).isTrue();
        assertThat(jdbc.queryForObject("select consecutive_failures "
                + "from job_market_sources where id=?", Integer.class, SOURCE))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select next_sync_at "
                + "from job_market_sources where id=?", java.sql.Timestamp.class, SOURCE)
                .toInstant()).isEqualTo(NOW.plusSeconds(5 * 60 + 5));
        assertThat(sync.history(null, 1, 1).items()).hasSize(1);
        assertThat(sync.history(SOURCE, 2, 1).items()).isEmpty();
        assertThatThrownBy(() -> sync.history(null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sync.history(null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sync.claimDue(0, "worker", "request", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sync.claimOne(SOURCE, "", "request", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
