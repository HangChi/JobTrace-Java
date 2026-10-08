package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.SourceSyncStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresSourceSyncStore implements SourceSyncStore {
    private static final long LEASE_SECONDS = 30 * 60;
    private final JdbcTemplate jdbc;

    public PostgresSourceSyncStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<Lease> claimOne(UUID sourceId, String workerId,
            String requestId, Instant now) {
        return claim(sourceId, workerId, requestId, now, "admin");
    }

    private Optional<Lease> claim(UUID sourceId, String workerId,
            String requestId, Instant now, String trigger) {
        validateIdentity(workerId, requestId);
        var candidates = jdbc.query("""
                select id,lease_run_id from job_market_sources
                where id=? and status='active' and (lease_until is null or lease_until<?)
                for update skip locked
                """, (rs, row) -> new PriorLease(rs.getObject("id", UUID.class),
                rs.getObject("lease_run_id", UUID.class)), sourceId, Timestamp.from(now));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        PriorLease prior = candidates.getFirst();
        if (prior.runId() != null) {
            jdbc.update("""
                    update job_market_sync_runs set status='failed',
                      finished_at=greatest(started_at,?),error_code='lease_expired',
                      error_summary='The source lease expired before completion.'
                    where id=? and status='running'
                    """, Timestamp.from(now), prior.runId());
        }
        UUID runId = jdbc.queryForObject("""
                insert into job_market_sync_runs(source_id,trigger,worker_id,
                  request_id,started_at)
                values (?,?,?,?,?) returning id
                """, UUID.class, sourceId,
                new SqlParameterValue(Types.OTHER, trigger), workerId, requestId,
                Timestamp.from(now));
        jdbc.update("""
                update job_market_sources set lease_until=?,leased_by=?,lease_run_id=?,
                  last_attempt_at=? where id=?
                """, Timestamp.from(now.plusSeconds(LEASE_SECONDS)), workerId,
                runId, Timestamp.from(now), sourceId);
        return jdbc.query("""
                select id,adapter::text,external_key,base_url,allowed_hosts,
                  country_codes,consecutive_failures
                from job_market_sources where id=?
                """, (rs, row) -> lease(rs, runId, workerId), sourceId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public List<Lease> claimDue(int limit, String workerId, String requestId,
            Instant now) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("无效的同步批次大小。");
        }
        validateIdentity(workerId, requestId);
        var due = jdbc.query("""
                select id from job_market_sources
                where status='active' and next_sync_at<=?
                  and (lease_until is null or lease_until<?)
                order by next_sync_at,id for update skip locked limit ?
                """, (rs, row) -> rs.getObject("id", UUID.class),
                Timestamp.from(now), Timestamp.from(now), limit);
        var claims = new ArrayList<Lease>();
        for (UUID id : due) {
            claim(id, workerId, requestId, now, "scheduled").ifPresent(claims::add);
        }
        return List.copyOf(claims);
    }

    @Transactional
    public boolean complete(Lease lease, Instant now, boolean succeeded) {
        return complete(lease, now, succeeded ? Completion.success()
                : Completion.failure("source_failure", "Source synchronization failed."));
    }

    @Override
    @Transactional
    public boolean complete(Lease lease, Instant now, Completion completion) {
        if (lease == null || now == null || completion == null) {
            throw new IllegalArgumentException("同步结果不能为空。");
        }
        var owned = jdbc.query("""
                select sync_interval_minutes,consecutive_failures from job_market_sources
                where id=? and status='active' and leased_by=? and lease_run_id=?
                for update
                """, (rs, row) -> new SourceSchedule(rs.getInt("sync_interval_minutes"),
                rs.getInt("consecutive_failures")), lease.sourceId(),
                lease.workerId(), lease.runId());
        if (owned.isEmpty()) {
            return false;
        }
        int updated = jdbc.update("""
                update job_market_sync_runs set status=?,finished_at=greatest(started_at,?),
                  discovered_count=?,created_count=?,updated_count=?,stale_count=?,
                  closed_count=?,rejected_count=?,error_code=?,error_summary=?
                where id=? and source_id=? and worker_id=? and status='running'
                """, new SqlParameterValue(Types.OTHER, completion.status()),
                Timestamp.from(now), completion.discovered(),
                completion.created(), completion.updated(), completion.stale(),
                completion.closed(), completion.rejected(), completion.errorCode(),
                completion.errorSummary(), lease.runId(), lease.sourceId(), lease.workerId());
        if (updated != 1) {
            return false;
        }
        SourceSchedule schedule = owned.getFirst();
        boolean failed = "failed".equals(completion.status());
        long delayMinutes = failed ? Math.min(360,
                5L << Math.min(6, schedule.consecutiveFailures()))
                : schedule.intervalMinutes();
        jdbc.update("""
                update job_market_sources set lease_until=null,leased_by=null,
                  lease_run_id=null,next_sync_at=?,consecutive_failures=?,
                  last_success_at=case when ? then ? else last_success_at end,
                  updated_at=? where id=? and lease_run_id=?
                """, Timestamp.from(now.plus(delayMinutes, ChronoUnit.MINUTES)),
                failed ? schedule.consecutiveFailures() + 1 : 0, !failed,
                Timestamp.from(now), Timestamp.from(now), lease.sourceId(), lease.runId());
        return true;
    }

    @Override
    public RunPage history(UUID sourceId, int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("无效的同步记录分页。");
        }
        int total = jdbc.queryForObject("""
                select count(*) from job_market_sync_runs
                where (?::uuid is null or source_id=?)
                """, Integer.class, sourceId, sourceId);
        List<Run> items = jdbc.query("""
                select id,source_id,trigger::text,status::text,started_at,finished_at,
                  discovered_count,created_count,updated_count,stale_count,
                  closed_count,rejected_count,error_code,error_summary
                from job_market_sync_runs
                where (?::uuid is null or source_id=?)
                order by started_at desc,id limit ? offset ?
                """, (rs, row) -> new Run(rs.getObject("id", UUID.class),
                rs.getObject("source_id", UUID.class), rs.getString("trigger"),
                rs.getString("status"), instant(rs, "started_at"),
                instant(rs, "finished_at"), new Counts(rs.getInt("discovered_count"),
                rs.getInt("created_count"), rs.getInt("updated_count"),
                rs.getInt("stale_count"), rs.getInt("closed_count"),
                rs.getInt("rejected_count")), rs.getString("error_code"),
                rs.getString("error_summary")), sourceId, sourceId, limit,
                (page - 1) * limit);
        return new RunPage(items, page, limit, total);
    }

    private static Lease lease(ResultSet rs, UUID runId, String workerId)
            throws SQLException {
        return new Lease(rs.getObject("id", UUID.class), runId, workerId,
                rs.getString("adapter"), rs.getString("external_key"),
                rs.getString("base_url"), array(rs, "allowed_hosts"),
                array(rs, "country_codes"), rs.getInt("consecutive_failures"));
    }

    private static List<String> array(ResultSet rs, String column) throws SQLException {
        var sqlArray = rs.getArray(column);
        if (sqlArray == null) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (Object value : (Object[]) sqlArray.getArray()) {
            result.add((String) value);
        }
        return List.copyOf(result);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp time = rs.getTimestamp(column);
        return time == null ? null : time.toInstant();
    }

    private static void validateIdentity(String workerId, String requestId) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 100
                || requestId == null || requestId.isBlank() || requestId.length() > 200) {
            throw new IllegalArgumentException("同步任务身份无效。");
        }
    }

    private record PriorLease(UUID sourceId, UUID runId) {}

    private record SourceSchedule(int intervalMinutes, int consecutiveFailures) {}
}
