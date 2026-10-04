package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountProfileReadQuery;
import com.jobtrace.identityaccess.domain.AccountProfileResult;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAccountProfileReadQuery implements AccountProfileReadQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAccountProfileReadQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AccountProfileResult> findByOwner(String ownerId) {
        var parameters = new MapSqlParameterSource("ownerId", ownerId);
        return jdbc.query("""
                select id,display_name,image,recovery_email,
                  recovery_email_verified_at is not null as email_verified
                from users where id=:ownerId
                """, parameters, result -> result.next()
                ? Optional.of(new AccountProfileResult(result.getString("id"),
                        result.getString("display_name"), result.getString("image"),
                        result.getString("recovery_email"),
                        result.getBoolean("email_verified")))
                : Optional.empty());
    }
}
