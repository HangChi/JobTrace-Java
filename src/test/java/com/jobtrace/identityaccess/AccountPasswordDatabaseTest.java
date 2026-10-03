package com.jobtrace.identityaccess;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

abstract class AccountPasswordDatabaseTest extends PostgresIntegrationTest {

    protected static final String OWNER = "password-owner";
    protected static final String OTHER_OWNER = "password-other";
    protected static final String CURRENT_SESSION = "password-current";
    protected static final String OTHER_SESSION = "password-other-device";
    protected static final String FOREIGN_SESSION = "password-foreign";
    protected static final String CURRENT_PASSWORD = "Current-Password";
    protected static final String CURRENT_HASH =
            "11223344556677889900aabbccddeeff:"
            + "55105274c79671401b76a5c5f7166e8aad680c177c1851b2a88399e2c3488238"
            + "13bc86ecda6bf6f8097e243c5c227c8388c9eb56eb6845fc6f132cdc77da7180";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    protected static RequestPostProcessor asOwner(String owner) {
        var authentication = new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new BridgeIdentity(owner, BridgeIdentity.Role.USER, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    @BeforeEach
    void seedPasswordState() {
        jdbc.execute("drop table if exists sessions, accounts, users cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/account-password-change.sql")).execute(dataSource);
        insertOwner(OWNER);
        insertOwner(OTHER_OWNER);
        insertCredential("credential-owner", OWNER, CURRENT_HASH);
        insertCredential("credential-other", OTHER_OWNER, CURRENT_HASH);
        insertSession(CURRENT_SESSION, OWNER);
        insertSession(OTHER_SESSION, OWNER);
        insertSession(FOREIGN_SESSION, OTHER_OWNER);
    }

    protected void insertOwner(String owner) {
        jdbc.update("""
                insert into users(id,display_name,email)
                values (?,?,?)
                """, owner, owner, owner + "@users.test");
    }

    protected void insertCredential(String id, String owner, String passwordHash) {
        jdbc.update("""
                insert into accounts(id,account_id,provider_id,user_id,password)
                values (?,?, 'credential', ?, ?)
                """, id, owner, owner, passwordHash);
    }

    protected void insertSession(String id, String owner) {
        jdbc.update("""
                insert into sessions(id,expires_at,token,user_id)
                values (?, '2099-01-01T00:00:00Z', ?, ?)
                """, id, "token-" + id, owner);
    }

    protected String ownerHash() {
        return jdbc.queryForObject(
                "select password from accounts where user_id=?", String.class, OWNER);
    }

    protected List<String> ownerSessions() {
        return jdbc.queryForList(
                "select id from sessions where user_id=? order by id", String.class, OWNER);
    }
}
