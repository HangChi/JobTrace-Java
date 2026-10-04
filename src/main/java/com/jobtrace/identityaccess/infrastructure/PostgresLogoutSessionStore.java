package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.LogoutSessionStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** A signed token can revoke only its exact database session row. */
@Repository
public class PostgresLogoutSessionStore implements LogoutSessionStore {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresLogoutSessionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean deleteByToken(String verifiedToken) {
        var parameters = new MapSqlParameterSource("token", verifiedToken);
        return jdbc.update("delete from sessions where token=:token", parameters) > 0;
    }
}
