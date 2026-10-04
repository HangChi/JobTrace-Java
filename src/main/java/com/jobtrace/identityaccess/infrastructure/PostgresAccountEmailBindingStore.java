package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountEmailBindingStore;
import com.jobtrace.identityaccess.domain.AccountEmailBindingStateException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Serializes code attempts and atomically binds a verified owner email. */
@Repository
public class PostgresAccountEmailBindingStore implements AccountEmailBindingStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresAccountEmailBindingStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public boolean bind(String ownerId, String currentSessionId, String accountId,
            String expectedPasswordHash, String normalizedEmail, String codeHash) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("currentSessionId", currentSessionId)
                .addValue("accountId", accountId)
                .addValue("expectedPasswordHash", expectedPasswordHash)
                .addValue("email", normalizedEmail)
                .addValue("codeHash", codeHash);
        Boolean result = transactions.execute(status -> {
            var sessions = jdbc.query("""
                    select id from sessions
                    where id=:currentSessionId and user_id=:ownerId and expires_at>now()
                    for update
                    """, parameters, (row, index) -> row.getString("id"));
            var accounts = jdbc.query("""
                    select id from accounts
                    where id=:accountId and user_id=:ownerId
                      and provider_id='credential' and password=:expectedPasswordHash
                    for update
                    """, parameters, (row, index) -> row.getString("id"));
            if (sessions.size() != 1 || accounts.size() != 1) {
                throw new AccountEmailBindingStateException();
            }
            var attempts = jdbc.query("""
                    select code_id,matched from verify_email_code_attempt(
                      :email,'email_binding',:ownerId,:codeHash)
                    """, parameters, (row, index) -> row.getBoolean("matched"));
            if (attempts.isEmpty() || !attempts.getFirst()) {
                return false;
            }
            int updated = jdbc.update("""
                    update users set recovery_email=:email,
                      recovery_email_verified_at=now(),email_verified=true,
                      updated_at=now()
                    where id=:ownerId
                    """, parameters);
            if (updated != 1) {
                throw new AccountEmailBindingStateException();
            }
            return true;
        });
        return Boolean.TRUE.equals(result);
    }
}
