package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.SyncRunHistoryQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresSyncRunHistoryQuery implements SyncRunHistoryQuery {
    private final JdbcTemplate jdbc;

    public PostgresSyncRunHistoryQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
                (long) (page - 1) * limit);
        return new RunPage(items, page, limit, total);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
