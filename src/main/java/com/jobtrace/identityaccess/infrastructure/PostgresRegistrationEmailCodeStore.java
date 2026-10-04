package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.RegistrationEmailCodeStore;
import com.jobtrace.identityaccess.domain.RegistrationEmailConflictException;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Atomically replaces only anonymous registration-purpose codes. */
@Repository
public class PostgresRegistrationEmailCodeStore implements RegistrationEmailCodeStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PostgresEmailCodeRateLimiter limits;

    public PostgresRegistrationEmailCodeStore(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresEmailCodeRateLimiter limits) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.limits = limits;
    }

    @Override
    public void consumeSourceAttempt() {
        limits.consumeSource("registration-email-code");
    }

    @Override
    public void issue(String email, String codeHash) {
        var parameters = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("codeHash", codeHash)
                .addValue("codeId", UUID.randomUUID());
        checkConflict(parameters);
        limits.consumeAddress(email);
        transactions.executeWithoutResult(status -> {
            checkConflict(parameters);
            jdbc.update("""
                    update email_verification_codes set consumed_at=now()
                    where lower(email)=lower(:email) and purpose='registration'
                      and user_id is null and consumed_at is null
                    """, parameters);
            jdbc.update("""
                    insert into email_verification_codes
                      (id,email,purpose,user_id,code_hash,expires_at)
                    values (:codeId,:email,'registration',null,:codeHash,
                      now()+interval '10 minutes')
                    """, parameters);
        });
    }

    private void checkConflict(MapSqlParameterSource parameters) {
        Integer conflicts = jdbc.queryForObject("""
                select count(*) from users
                where lower(recovery_email)=lower(:email)
                """, parameters, Integer.class);
        if (conflicts != null && conflicts > 0) {
            throw new RegistrationEmailConflictException();
        }
    }
}
