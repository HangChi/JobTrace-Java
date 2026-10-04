package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountEmailCodeStore;
import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
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
    private final PostgresEmailCodeRateLimiter limits;

    public PostgresAccountEmailCodeStore(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresEmailCodeRateLimiter limits) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.limits = limits;
    }

    @Override
    public void consumeSourceAttempt() {
        limits.consumeSource("binding-email-code");
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
        limits.consumeAddress(email);
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

}
