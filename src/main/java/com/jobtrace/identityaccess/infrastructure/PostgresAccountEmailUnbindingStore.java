package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountEmailUnbindingStore;
import com.jobtrace.identityaccess.domain.AccountEmailRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailUnbindingStateException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Reuses the legacy rate limiter and clears one owner's email state atomically. */
@Repository
public class PostgresAccountEmailUnbindingStore implements AccountEmailUnbindingStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresAccountEmailUnbindingStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void consumeChangeAttempt(String ownerId) {
        Boolean allowed = jdbc.queryForObject("""
                select consume_auth_rate_limit(:keyHash,'email-change',5,900)
                """, new MapSqlParameterSource("keyHash", sha256(ownerId)), Boolean.class);
        if (allowed == null) {
            throw new AccountEmailUnbindingStateException();
        }
        if (!allowed) {
            throw new AccountEmailRateLimitedException();
        }
    }

    @Override
    public void unbind(String ownerId, String currentSessionId,
            String accountId, String expectedPasswordHash) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("currentSessionId", currentSessionId)
                .addValue("accountId", accountId)
                .addValue("expectedPasswordHash", expectedPasswordHash);
        transactions.executeWithoutResult(status -> {
            var sessions = jdbc.query("""
                    select id from sessions
                    where id=:currentSessionId and user_id=:ownerId and expires_at>now()
                    for update
                    """, parameters, (result, row) -> result.getString("id"));
            var accounts = jdbc.query("""
                    select id from accounts
                    where id=:accountId and user_id=:ownerId
                      and provider_id='credential' and password=:expectedPasswordHash
                    for update
                    """, parameters, (result, row) -> result.getString("id"));
            if (sessions.size() != 1 || accounts.size() != 1) {
                throw new AccountEmailUnbindingStateException();
            }
            int updated = jdbc.update("""
                    update users set recovery_email=null,
                      recovery_email_verified_at=null,email_verified=false,updated_at=now()
                    where id=:ownerId
                    """, parameters);
            if (updated != 1) {
                throw new AccountEmailUnbindingStateException();
            }
            jdbc.update("""
                    update email_verification_codes set consumed_at=now()
                    where user_id=:ownerId and consumed_at is null
                    """, parameters);
        });
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
