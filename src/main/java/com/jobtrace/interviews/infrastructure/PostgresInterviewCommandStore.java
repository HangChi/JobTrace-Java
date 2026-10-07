package com.jobtrace.interviews.infrastructure;

import com.jobtrace.interviews.application.InterviewCommandStore;
import com.jobtrace.interviews.domain.InterviewNotFoundException;
import com.jobtrace.interviews.domain.InterviewWriteException;
import com.jobtrace.interviews.domain.InterviewMutation.Create;
import com.jobtrace.interviews.domain.InterviewMutation.Update;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresInterviewCommandStore implements InterviewCommandStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresInterviewCommandStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public UUID create(String ownerId, Create command) {
        var values = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("applicationId", command.applicationId())
                .addValue("stageOccurrenceId", command.stageOccurrenceId())
                .addValue("stage", command.stage()).addValue("interviewedOn", command.interviewedOn())
                .addValue("payload", mapper.writeValueAsString(command.payload()));
        try {
            return jdbc.queryForObject("""
                    select id from public.create_interview_review_for_owner(
                      :ownerId,:applicationId,:stageOccurrenceId,
                      cast(:stage as recruitment_stage),:interviewedOn,cast(:payload as jsonb))
                    """, values, UUID.class);
        } catch (DataAccessException exception) {
            throw translate(exception, true);
        }
    }

    @Override
    public void update(String ownerId, UUID id, Update command) {
        var values = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("id", id)
                .addValue("version", command.version())
                .addValue("payload", mapper.writeValueAsString(command.payload()));
        try {
            jdbc.queryForObject("""
                    select id from public.update_interview_review_for_owner(
                      :ownerId,:id,:version,cast(:payload as jsonb))
                    """, values, UUID.class);
        } catch (DataAccessException exception) {
            throw translate(exception, false);
        }
    }

    @Override
    public boolean delete(String ownerId, UUID id) {
        return jdbc.update("delete from interview_reviews where id=:id and owner_id=:ownerId",
                Map.of("id", id, "ownerId", ownerId)) > 0;
    }

    private RuntimeException translate(DataAccessException exception, boolean create) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                String message = sql.getMessage();
                return switch (sql.getSQLState()) {
                    case "P0002" -> new InterviewNotFoundException();
                    case "40001" -> new InterviewWriteException(InterviewWriteException.Kind.CONFLICT,
                            "面经已在其他页面更新，请刷新后重试。");
                    case "23505" -> new InterviewWriteException(InterviewWriteException.Kind.CONFLICT,
                            "该阶段已经记录过面经。");
                    case "23514" -> new InterviewWriteException(InterviewWriteException.Kind.VALIDATION,
                            message != null && message.contains("review_publish_incomplete")
                                    ? "请先完成复盘，再公开面经。" : "请先填写面经内容，再完成复盘。");
                    case "22023", "22P02" -> new InterviewWriteException(
                            InterviewWriteException.Kind.VALIDATION,
                            message != null && message.contains("terminal_application")
                                    ? "Offer 或拒绝后的投递不能再记录新的面试。"
                                    : create ? "面试阶段或日期无效。" : "面试 / 测评日期无效。");
                    default -> exception;
                };
            }
            cause = cause.getCause();
        }
        return exception;
    }
}
