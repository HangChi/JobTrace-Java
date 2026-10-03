package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountSessionCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** One atomic, parameterized deletion constrained by both opaque ID and verified owner. */
@Repository
public class PostgresAccountSessionCommand implements AccountSessionCommand {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAccountSessionCommand(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean revoke(String ownerId, String sessionId) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("sessionId", sessionId);
        return !jdbc.query("""
                delete from sessions
                where id=:sessionId and user_id=:ownerId
                returning id
                """, parameters, (result, row) -> result.getString("id")).isEmpty();
    }
}
