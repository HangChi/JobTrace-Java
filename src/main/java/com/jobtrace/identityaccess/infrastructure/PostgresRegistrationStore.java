package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.RegistrationStore;
import com.jobtrace.identityaccess.domain.RegistrationConflictException;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses the legacy attempt routine, then atomically creates a Better Auth-shaped account. */
@Repository
public class PostgresRegistrationStore implements RegistrationStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresRegistrationStore(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public boolean verifyCode(String email, String codeHash) {
        var parameters = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("codeHash", codeHash);
        return jdbc.query("""
                select matched from verify_email_code_attempt(
                    :email,'registration',null,:codeHash)
                """, parameters, result ->
                result.next() && result.getBoolean("matched"));
    }

    @Override
    public void createAccount(String username, String email, String passwordHash,
            String displayName) {
        var parameters = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("email", email)
                .addValue("internalEmail", username + "@users.jobtrace.local")
                .addValue("displayName", displayName)
                .addValue("passwordHash", passwordHash)
                .addValue("userId", UUID.randomUUID().toString())
                .addValue("accountId", UUID.randomUUID().toString());
        try {
            transactions.executeWithoutResult(status -> {
                checkConflicts(parameters);
                jdbc.update("""
                        insert into users
                          (id,display_name,email,email_verified,username,display_username,
                           recovery_email,recovery_email_verified_at,role,updated_at)
                        values
                          (:userId,:displayName,:internalEmail,true,:username,:username,
                           :email,now(),'user',now())
                        """, parameters);
                jdbc.update("""
                        insert into accounts
                          (id,account_id,provider_id,user_id,password,updated_at)
                        values (:accountId,:userId,'credential',:userId,:passwordHash,now())
                        """, parameters);
            });
        } catch (DuplicateKeyException exception) {
            if (exists("lower(recovery_email)=lower(:email)", parameters)) {
                throw new RegistrationConflictException("email");
            }
            if (exists("username=:username", parameters)) {
                throw new RegistrationConflictException("username");
            }
            if (exists("email=:internalEmail", parameters)) {
                throw new RegistrationConflictException("username");
            }
            throw exception;
        }
    }

    private void checkConflicts(MapSqlParameterSource parameters) {
        if (exists("lower(recovery_email)=lower(:email)", parameters)) {
            throw new RegistrationConflictException("email");
        }
        if (exists("username=:username", parameters)) {
            throw new RegistrationConflictException("username");
        }
        if (exists("email=:internalEmail", parameters)) {
            throw new RegistrationConflictException("username");
        }
    }

    private boolean exists(String predicate, MapSqlParameterSource parameters) {
        Integer count = jdbc.queryForObject("select count(*) from users where " + predicate,
                parameters, Integer.class);
        return count != null && count > 0;
    }
}
