package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountPasswordStore;
import com.jobtrace.identityaccess.domain.AccountPasswordCredential;
import com.jobtrace.identityaccess.domain.AccountPasswordStateException;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses owner-scoped reads and one transaction for credential replacement and session cleanup. */
@Repository
public class PostgresAccountPasswordStore implements AccountPasswordStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresAccountPasswordStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<AccountPasswordCredential> findCredential(
            String ownerId, String currentSessionId) {
        var parameters = parameters(ownerId, currentSessionId);
        return jdbc.query("""
                select a.id,a.password
                from accounts a
                where a.user_id=:ownerId
                  and a.provider_id='credential'
                  and a.password is not null
                  and exists (
                    select 1 from sessions s
                    where s.id=:currentSessionId
                      and s.user_id=:ownerId
                      and s.expires_at>now()
                  )
                order by a.id
                fetch first 1 row only
                """, parameters, (result, row) -> new AccountPasswordCredential(
                        result.getString("id"), result.getString("password")))
                .stream().findFirst();
    }

    @Override
    public void replaceCredentialAndRevokeOtherSessions(
            String ownerId,
            String currentSessionId,
            String accountId,
            String expectedPasswordHash,
            String replacementPasswordHash) {
        var parameters = parameters(ownerId, currentSessionId)
                .addValue("accountId", accountId)
                .addValue("expectedPasswordHash", expectedPasswordHash)
                .addValue("replacementPasswordHash", replacementPasswordHash);
        transactions.executeWithoutResult(status -> {
            var current = jdbc.query("""
                    select id from sessions
                    where id=:currentSessionId
                      and user_id=:ownerId
                      and expires_at>now()
                    for update
                    """, parameters, (result, row) -> result.getString("id"));
            if (current.isEmpty()) {
                throw new AccountPasswordStateException();
            }
            int updated = jdbc.update("""
                    update accounts
                    set password=:replacementPasswordHash,updated_at=now()
                    where id=:accountId
                      and user_id=:ownerId
                      and provider_id='credential'
                      and password=:expectedPasswordHash
                    """, parameters);
            if (updated != 1) {
                throw new AccountPasswordStateException();
            }
            jdbc.update("""
                    delete from sessions
                    where user_id=:ownerId and id<>:currentSessionId
                    """, parameters);
        });
    }

    private static MapSqlParameterSource parameters(
            String ownerId, String currentSessionId) {
        return new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("currentSessionId", currentSessionId);
    }
}
