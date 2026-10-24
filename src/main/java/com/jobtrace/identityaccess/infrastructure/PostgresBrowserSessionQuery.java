package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.BrowserSessionQuery;
import com.jobtrace.identityaccess.domain.BrowserSession;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Rechecks current session and account state on each browser session lookup. */
@Repository
public class PostgresBrowserSessionQuery implements BrowserSessionQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresBrowserSessionQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BrowserSession> findActive(String token) {
        var parameters = new MapSqlParameterSource("token", token);
        return jdbc.query("""
                select s.id,s.expires_at,u.id as user_id,u.display_name,u.image,u.role
                from sessions s join users u on u.id=s.user_id
                where s.token=:token and s.expires_at>now() and u.disabled=false
                limit 1
                """, parameters, result -> result.next()
                ? Optional.of(new BrowserSession(result.getString("id"),
                        result.getTimestamp("expires_at").toInstant(),
                        result.getString("user_id"), result.getString("display_name"),
                        result.getString("image"), result.getString("role")))
                : Optional.empty());
    }
}
