package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.application.RegistrationAttemptLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Shares the legacy source, address, and cooldown rules across code purposes. */
@Repository
public class PostgresEmailCodeRateLimiter implements RegistrationAttemptLimiter {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresEmailCodeRateLimiter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void consumeSource(String fallbackKey) {
        consume(fallbackKey, "email-code-ip", 100, 3600);
    }

    public void consumeAddress(String email) {
        consume(email, "email-code-address", 5, 3600);
        consume(email, "email-code-cooldown", 1, 60);
    }

    @Override
    public void consumeRegistrationAttempt() {
        consume("local-register", "register", 5, 60);
    }

    private void consume(String key, String action, int limit, int windowSeconds) {
        var parameters = new MapSqlParameterSource()
                .addValue("keyHash", sha256(key))
                .addValue("action", action)
                .addValue("requestLimit", limit)
                .addValue("windowSeconds", windowSeconds);
        Boolean allowed = jdbc.queryForObject("""
                select consume_auth_rate_limit(:keyHash,:action,:requestLimit,:windowSeconds)
                """, parameters, Boolean.class);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new AccountEmailCodeRateLimitedException();
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
