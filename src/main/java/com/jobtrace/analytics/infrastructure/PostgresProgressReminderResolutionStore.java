package com.jobtrace.analytics.infrastructure;

import com.jobtrace.analytics.application.ProgressReminderResolution;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresProgressReminderResolutionStore implements ProgressReminderResolution.Store {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresProgressReminderResolutionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean resolve(String ownerId, UUID stageOccurrenceId,
            ProgressReminderResolution.Resolution resolution) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("stageOccurrenceId", stageOccurrenceId)
                .addValue("resolution", resolution.wire());
        return !jdbc.query("""
                insert into progress_reminder_completions(owner_id, stage_occurrence_id, resolution)
                select :ownerId, stage.id, :resolution
                from application_stage_occurrences stage
                join applications application on application.id = stage.application_id
                where stage.id = :stageOccurrenceId
                  and application.owner_id = :ownerId
                  and application.status = 'submitted'
                on conflict (owner_id, stage_occurrence_id) do update set
                  resolution = excluded.resolution,
                  reminder_id = null,
                  updated_at = now()
                returning id
                """, parameters, (rs, row) -> rs.getObject("id", UUID.class)).isEmpty();
    }
}
