package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.SourceCatalogStore;
import com.jobtrace.jobmarket.domain.SourceHealth;
import com.jobtrace.jobmarket.domain.SourceRegistration;
import com.jobtrace.jobmarket.domain.SourceUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresSourceCatalogStore implements SourceCatalogStore {

    private static final String SOURCE_SELECT = """
            select source.id, company.id as company_id,
              company.canonical_name, company.company_type, company.industry,
              source.adapter::text as adapter, source.base_url,
              source.status::text as status, source.next_sync_at,
              source.last_attempt_at, source.last_success_at, source.consecutive_failures,
              run.id as run_id, run.source_id as run_source_id,
              run.trigger::text as run_trigger, run.status::text as run_status,
              run.started_at, run.finished_at, run.discovered_count,
              run.created_count, run.updated_count, run.stale_count,
              run.closed_count, run.rejected_count, run.error_code, run.error_summary
            from job_market_sources source
            join job_market_companies company on company.id = source.company_id
            left join lateral (
              select * from job_market_sync_runs recent
              where recent.source_id = source.id
              order by recent.started_at desc, recent.id desc limit 1
            ) run on true
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public PostgresSourceCatalogStore(
            JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public List<SourceHealth> list() {
        return jdbc.query(SOURCE_SELECT + " order by company.canonical_name, source.id",
                (rs, row) -> map(rs));
    }

    @Override
    @Transactional
    public Optional<SourceHealth> register(SourceRegistration registration) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from job_market_companies where id = ?)",
                Boolean.class, registration.companyId()))) {
            return Optional.empty();
        }
        UUID sourceId = UUID.randomUUID();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into job_market_sources(
                      id, company_id, adapter, external_key, base_url,
                      allowed_hosts, country_codes, access_basis, is_official,
                      sync_interval_minutes, status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'paused')
                    """);
            statement.setObject(1, sourceId);
            statement.setObject(2, registration.companyId());
            statement.setObject(3, registration.adapter(), Types.OTHER);
            statement.setString(4, registration.externalKey());
            statement.setString(5, registration.baseUrl());
            statement.setArray(6, connection.createArrayOf(
                    "text", registration.allowedHosts().toArray(String[]::new)));
            statement.setArray(7, connection.createArrayOf(
                    "text", registration.countryCodes().toArray(String[]::new)));
            statement.setString(8, registration.accessBasis());
            statement.setBoolean(9, registration.isOfficial());
            statement.setInt(10, registration.syncIntervalMinutes());
            return statement;
        });
        return find(sourceId);
    }

    @Override
    @Transactional
    public Optional<SourceHealth> update(UUID sourceId, SourceUpdate change) {
        var rows = jdbc.query("""
                select company_id, lease_run_id from job_market_sources
                where id = ? for update
                """, (rs, row) -> new SourceLock(
                rs.getObject("company_id", UUID.class),
                rs.getObject("lease_run_id", UUID.class)), sourceId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        SourceLock current = rows.getFirst();
        boolean disabling = "paused".equals(change.status())
                || "revoked".equals(change.status());
        if (disabling && current.leaseRunId() != null) {
            jdbc.update("""
                    update job_market_sync_runs set status = 'failed',
                      finished_at = greatest(started_at, now()),
                      error_code = 'source_disabled',
                      error_summary = 'The source was disabled during synchronization.'
                    where id = ? and status = 'running'
                    """, current.leaseRunId());
        }
        var parameters = new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("status", change.status(), Types.OTHER)
                .addValue("interval", change.syncIntervalMinutes())
                .addValue("basis", change.accessBasis())
                .addValue("disabling", disabling);
        namedJdbc.update("""
                update job_market_sources set
                  status = coalesce(:status, status),
                  sync_interval_minutes = coalesce(:interval, sync_interval_minutes),
                  access_basis = coalesce(:basis, access_basis),
                  next_sync_at = case when cast(:status as text) = 'active'
                    then now() else next_sync_at end,
                  lease_until = case when :disabling then null else lease_until end,
                  leased_by = case when :disabling then null else leased_by end,
                  lease_run_id = case when :disabling then null else lease_run_id end,
                  updated_at = now()
                where id = :sourceId
                """, parameters);
        refreshReadModelIfAvailable(current.companyId());
        return find(sourceId);
    }

    private Optional<SourceHealth> find(UUID sourceId) {
        var rows = namedJdbc.query(SOURCE_SELECT + " where source.id = :sourceId",
                new MapSqlParameterSource("sourceId", sourceId),
                (rs, row) -> map(rs));
        return rows.stream().findFirst();
    }

    private void refreshReadModelIfAvailable(UUID companyId) {
        Boolean available = jdbc.queryForObject("""
                select to_regprocedure(
                  'public.refresh_job_market_company_read_model(uuid)') is not null
                """, Boolean.class);
        if (Boolean.TRUE.equals(available)) {
            jdbc.queryForList("select public.refresh_job_market_company_read_model(?)",
                    companyId);
        }
    }

    private static SourceHealth map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        var company = new SourceHealth.Company(
                rs.getObject("company_id", UUID.class), rs.getString("canonical_name"),
                rs.getString("company_type"), rs.getString("industry"));
        SourceHealth.LatestRun run = null;
        UUID runId = rs.getObject("run_id", UUID.class);
        if (runId != null) {
            var counts = new SourceHealth.Counts(rs.getInt("discovered_count"),
                    rs.getInt("created_count"), rs.getInt("updated_count"),
                    rs.getInt("stale_count"), rs.getInt("closed_count"),
                    rs.getInt("rejected_count"));
            run = new SourceHealth.LatestRun(runId,
                    rs.getObject("run_source_id", UUID.class),
                    rs.getString("run_trigger"), rs.getString("run_status"),
                    instant(rs, "started_at"), instant(rs, "finished_at"), counts,
                    rs.getString("error_code"), rs.getString("error_summary"));
        }
        return new SourceHealth(id, company, rs.getString("adapter"),
                rs.getString("base_url"), rs.getString("status"),
                instant(rs, "next_sync_at"), instant(rs, "last_attempt_at"),
                instant(rs, "last_success_at"), rs.getInt("consecutive_failures"), run);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record SourceLock(UUID companyId, UUID leaseRunId) {
    }
}
