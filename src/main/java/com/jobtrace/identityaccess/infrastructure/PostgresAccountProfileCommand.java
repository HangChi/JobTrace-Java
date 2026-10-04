package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountProfileCommand;
import com.jobtrace.identityaccess.domain.AccountProfileResult;
import com.jobtrace.identityaccess.domain.AccountProfileUpdate;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** One owner-scoped update returning the established profile projection. */
@Repository
public class PostgresAccountProfileCommand implements AccountProfileCommand {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAccountProfileCommand(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AccountProfileResult> update(AccountProfileUpdate command) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", command.ownerId())
                .addValue("displayName", command.displayName())
                .addValue("image", command.image());
        return jdbc.query("""
                update users
                set display_name=:displayName,image=:image,updated_at=now()
                where id=:ownerId
                returning id,display_name,image,recovery_email,
                  recovery_email_verified_at is not null as email_verified
                """, parameters, (result, row) -> new AccountProfileResult(
                result.getString("id"), result.getString("display_name"),
                result.getString("image"), result.getString("recovery_email"),
                result.getBoolean("email_verified"))).stream().findFirst();
    }
}
