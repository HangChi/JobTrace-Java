package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountEmailCodeStore;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses existing PostgreSQL limits and atomically replaces one owner's binding code. */
@Repository
public class PostgresAccountEmailCodeStore implements AccountEmailCodeStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresAccountEmailCodeStore(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void consumeSourceAttempt() {
        consume("binding-email-code", "email-code-ip", 100, 3600);
    }

    @Override
    public void issue(String ownerId, String sessionId, String email, String codeHash) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("sessionId", sessionId)
                .addValue("email", email)
                .addValue("codeHash", codeHash)
                .addValue("codeId", UUID.randomUUID());
        checkOwnerAndConflict(parameters, false);
        consume(email, "email-code-address", 5, 3600);
        consume(email, "email-code-cooldown", 1, 60);
        transactions.executeWithoutResult(status -> {
            checkOwnerAndConflict(parameters, true);
            jdbc.update("""
                    update email_verification_codes set consumed_at=now()
                    where lower(email)=lower(:email) and purpose='email_binding'
                      and user_id=:ownerId and consumed_at is null
                    """, parameters);
            jdbc.update("""
                    insert into email_verification_codes
                      (id,email,purpose,user_id,code_hash,expires_at)
                    values (:codeId,:email,'email_binding',:ownerId,:codeHash,
                      now()+interval '10 minutes')
                    """, parameters);
        });
    }

    private void checkOwnerAndConflict(MapSqlParameterSource parameters, boolean lockSession) {
        String lock = lockSession ? " for update" : "";
        var sessions = jdbc.query("""
                select id from sessions
                where id=:sessionId and user_id=:ownerId and expires_at>now()
                """ + lock, parameters, (row, index) -> row.getString("id"));
        if (sessions.size() != 1) {
            throw new AccountEmailCodeStateException();
        }
        Integer conflicts = jdbc.queryForObject("""
                select count(*) from users
                where lower(recovery_email)=lower(:email) and id<>:ownerId
                """, parameters, Integer.class);
        if (conflicts != null && conflicts > 0) {
            throw new AccountEmailConflictException();
        }
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
