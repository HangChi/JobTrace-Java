package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountSessionReadQuery;
import com.jobtrace.identityaccess.domain.AccountSession;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized, owner-scoped active-session read over the unchanged legacy table. */
@Repository
public class PostgresAccountSessionReadQuery implements AccountSessionReadQuery {

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAccountSessionReadQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AccountSession> listActive(String ownerId, String currentSessionId) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("currentSessionId", currentSessionId);
        return jdbc.query("""
                select id,created_at,expires_at,user_agent,
                  id=:currentSessionId as current_session
                from sessions
                where user_id=:ownerId and expires_at > now()
                order by created_at desc,id desc
                """, parameters, (rs, row) -> new AccountSession(
                rs.getString("id"), timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("expires_at")), rs.getString("user_agent"),
                rs.getBoolean("current_session")));
    }

    private static String timestamp(Timestamp value) {
        return UTC_MILLIS.format(value.toInstant());
    }
}
