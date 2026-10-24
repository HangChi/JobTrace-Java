package com.jobtrace.admin.infrastructure;

import com.jobtrace.admin.application.AdminAccessCommand;
import com.jobtrace.admin.domain.AdminAccessChange;
import com.jobtrace.admin.domain.AdminAccessResult;
import com.jobtrace.admin.domain.AdminUserNotFoundException;
import com.jobtrace.admin.domain.CurrentAdminRequiredException;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Calls the legacy atomic policy boundary; no access rule is duplicated here. */
@Repository
public class PostgresAdminAccessCommand implements AdminAccessCommand {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresAdminAccessCommand(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public AdminAccessResult execute(
            String actorUserId,
            String targetUserId,
            AdminAccessChange change) {
        var parameters = new MapSqlParameterSource()
                .addValue("actor", actorUserId)
                .addValue("target", targetUserId)
                .addValue("requestId", change.requestId())
                .addValue("expectedVersion", change.expectedVersion())
                .addValue("action", change.action().value())
                .addValue("reason", change.reason())
                .addValue("confirmSelf", change.confirmSelf());
        try {
            String json = jdbc.queryForObject("""
                    select public.change_user_access_as(
                      :actor,:target,cast(:requestId as uuid),:expectedVersion,
                      :action,:reason,:confirmSelf)::text
                    """, parameters, String.class);
            return result(json);
        } catch (DataAccessException exception) {
            String sqlState = sqlState(exception);
            if ("P0002".equals(sqlState)) {
                throw new AdminUserNotFoundException();
            }
            if ("42501".equals(sqlState)) {
                throw new CurrentAdminRequiredException();
            }
            throw exception;
        }
    }

    private AdminAccessResult result(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new AdminAccessResult(
                    AdminAccessResult.Outcome.parse(root.get("outcome").asText()),
                    text(root, "failureCode"),
                    text(root, "auditEventId"),
                    root.get("replayed").asBoolean(),
                    text(root, "userId"),
                    text(root, "role"),
                    bool(root, "disabled"),
                    number(root, "accessVersion"));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid administration command result", exception);
        }
    }

    private static String sqlState(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
