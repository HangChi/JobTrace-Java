package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.PasswordResetStore;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses the old verification table; token consumption, update and revocation share one transaction. */
@Repository
public class PostgresPasswordResetStore implements PasswordResetStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresPasswordResetStore(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override
    public void consumeAttempt(String source) {
        String keyHash = sha256(source == null ? "unknown" : source);
        Boolean allowed = jdbc.queryForObject("""
                select consume_auth_rate_limit(:keyHash,'password-reset',5,900)
                """, new MapSqlParameterSource("keyHash", keyHash), Boolean.class);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new AccountEmailCodeRateLimitedException();
        }
    }

    @Override
    public Optional<String> findVerifiedOwner(String email) {
        return jdbc.query("""
                select id from users
                where lower(recovery_email)=:email
                  and recovery_email_verified_at is not null and disabled=false
                order by id fetch first 1 row only
                """, new MapSqlParameterSource("email", email),
                (rs, row) -> rs.getString("id")).stream().findFirst();
    }

    @Override
    public void issue(String ownerId, String identifier) {
        jdbc.update("""
                insert into verification_tokens(id,identifier,value,expires_at)
                values (:id,:identifier,:ownerId,now()+interval '1 hour')
                """, new MapSqlParameterSource("id", UUID.randomUUID().toString())
                .addValue("identifier", identifier).addValue("ownerId", ownerId));
    }

    @Override
    public boolean valid(String identifier) {
        Integer count = jdbc.queryForObject("""
                select count(*) from verification_tokens
                where identifier=:identifier and expires_at>now()
                """, new MapSqlParameterSource("identifier", identifier), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean consumeAndReplace(String identifier, String passwordHash) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var params = new MapSqlParameterSource("identifier", identifier)
                    .addValue("passwordHash", passwordHash);
            var grants = jdbc.query("""
                    select id,value from verification_tokens
                    where identifier=:identifier and expires_at>now()
                    for update
                    """, params, (rs, row) -> new Grant(rs.getString("id"),
                    rs.getString("value")));
            if (grants.size() != 1) {
                return false;
            }
            Grant grant = grants.getFirst();
            params.addValue("id", grant.id()).addValue("ownerId", grant.ownerId());
            int updated = jdbc.update("""
                    update accounts set password=:passwordHash,updated_at=now()
                    where user_id=:ownerId and provider_id='credential'
                    """, params);
            if (updated != 1) {
                status.setRollbackOnly();
                return false;
            }
            jdbc.update("delete from sessions where user_id=:ownerId", params);
            jdbc.update("delete from verification_tokens where id=:id", params);
            return true;
        }));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Grant(String id, String ownerId) {}
}
