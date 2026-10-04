package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.LoginStore;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresLoginStore implements LoginStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public PostgresLoginStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Credential findCredential(String identifier, boolean email) {
        var params = new MapSqlParameterSource("identifier", identifier);
        String predicate = email
                ? "lower(u.recovery_email)=:identifier and u.recovery_email_verified_at is not null"
                : "u.username=:identifier";
        return jdbc.query("""
                select u.id, a.password, u.role, u.disabled
                from users u join accounts a on a.user_id=u.id and a.provider_id='credential'
                where """ + " " + predicate + " limit 1", params, result -> result.next()
                ? new Credential(result.getString("id"), result.getString("password"),
                        result.getString("role"), result.getBoolean("disabled")) : null);
    }

    @Override
    public String createSession(String userId, String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("token", token)
                .addValue("userId", userId)
                .addValue("expiresAt", java.sql.Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)))
                .addValue("userAgent", userAgent == null ? null
                        : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        jdbc.update("""
                insert into sessions(id,token,user_id,expires_at,user_agent,created_at,updated_at)
                values (:id,:token,:userId,:expiresAt,:userAgent,now(),now())
                """, params);
        return token;
    }
}
