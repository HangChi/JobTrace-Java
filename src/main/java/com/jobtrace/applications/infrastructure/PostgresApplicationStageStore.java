package com.jobtrace.applications.infrastructure;

import com.jobtrace.applications.application.ApplicationStageStore;
import com.jobtrace.applications.domain.ApplicationMutation.Stage;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.applications.domain.ApplicationWriteException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresApplicationStageStore implements ApplicationStageStore {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresApplicationStageStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(String ownerId, UUID applicationId, Stage stage) {
        var values = parameters(ownerId, applicationId, stage);
        try {
            jdbc.queryForObject("""
                    select id from public.add_stage_occurrence_for_owner(
                      :ownerId,:applicationId,cast(:stage as recruitment_stage),:occurredOn)
                    """, values, UUID.class);
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void update(String ownerId, UUID applicationId, UUID occurrenceId,
            int expectedVersion, Stage stage, LocalDate changeDate) {
        var values = parameters(ownerId, applicationId, stage)
                .addValue("occurrenceId", occurrenceId)
                .addValue("version", expectedVersion)
                .addValue("changeDate", changeDate);
        try {
            jdbc.queryForObject("""
                    select id from public.update_stage_occurrence_for_owner(
                      :ownerId,:applicationId,:occurrenceId,:version,
                      cast(:stage as recruitment_stage),:occurredOn,:changeDate)
                    """, values, UUID.class);
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void remove(String ownerId, UUID applicationId, UUID occurrenceId, LocalDate changeDate) {
        var values = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("applicationId", applicationId)
                .addValue("occurrenceId", occurrenceId).addValue("changeDate", changeDate);
        try {
            jdbc.queryForObject("""
                    select public.remove_stage_occurrence_for_owner(
                      :ownerId,:applicationId,:occurrenceId,:changeDate)
                    """, values, Object.class);
        } catch (DataAccessException exception) {
            throw translate(exception);
        }
    }

    private MapSqlParameterSource parameters(String ownerId, UUID applicationId, Stage stage) {
        return new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("applicationId", applicationId)
                .addValue("stage", stage.stage().value()).addValue("occurredOn", stage.occurredOn());
    }

    private RuntimeException translate(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                return switch (sql.getSQLState()) {
                    case "P0002" -> new ApplicationNotFoundException();
                    case "40001" -> new ApplicationWriteException(
                            ApplicationWriteException.Kind.CONFLICT, "记录已被更新，请刷新后重试。");
                    case "23505" -> new ApplicationWriteException(
                            ApplicationWriteException.Kind.CONFLICT,
                            "该招聘阶段已经记录过，不能重复添加。");
                    case "22023" -> new ApplicationWriteException(
                            ApplicationWriteException.Kind.VALIDATION,
                            sql.getMessage() != null && sql.getMessage().contains("terminal_application")
                                    ? "Offer 或拒绝后的投递不能再添加招聘阶段。" : "阶段日期无效。");
                    default -> exception;
                };
            }
            cause = cause.getCause();
        }
        return exception;
    }
}
