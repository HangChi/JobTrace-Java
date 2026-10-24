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

public abstract class AccountSessionReadDatabaseTest extends PostgresIntegrationTest {

    protected static final String OWNER = "owner-a";
    protected static final String OTHER_OWNER = "owner-b";
    protected static final String CURRENT = "session-current";

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
    void seedSessions() {
        jdbc.execute("drop table if exists sessions, users cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/account-session-read-model.sql")).execute(dataSource);
        jdbc.update("insert into users(id) values (?),(?)", OWNER, OTHER_OWNER);
        insert(CURRENT, OWNER, "2026-10-02T09:30:00Z", "2099-10-02T09:30:00Z",
                "Mozilla/5.0 (Macintosh)");
        insert("session-null-agent", OWNER, "2026-10-01T08:00:00Z",
                "2099-10-01T08:00:00Z", null);
        insert("session-older", OWNER, "2026-10-01T08:00:00Z",
                "2099-10-01T09:00:00Z", "Mozilla/5.0 (Linux)");
        insert("session-expired", OWNER, "2026-09-01T08:00:00Z",
                "2026-09-02T08:00:00Z", "Expired agent");
        insert("session-foreign", OTHER_OWNER, "2026-10-03T08:00:00Z",
                "2099-10-03T08:00:00Z", "Secret agent");
    }

    protected void insert(
            String id,
            String owner,
            String createdAt,
            String expiresAt,
            String userAgent) {
        jdbc.update("""
                insert into sessions(id,expires_at,token,created_at,updated_at,user_agent,user_id)
                values (?,?::timestamptz,?,?::timestamptz,?::timestamptz,?,?)
                """, id, expiresAt, "token-" + id, createdAt, createdAt, userAgent, owner);
    }
}
