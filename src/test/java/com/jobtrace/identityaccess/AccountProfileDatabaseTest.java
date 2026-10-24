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

abstract class AccountProfileDatabaseTest extends PostgresIntegrationTest {

    protected static final String OWNER = "profile-owner";
    protected static final String OTHER_OWNER = "profile-other";

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
    void seedProfiles() {
        jdbc.execute("drop table if exists sessions, users cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/account-profile-update.sql")).execute(dataSource);
        insert(OWNER, "Old Name", "https://img.test/old.png",
                "owner@example.test", "2026-10-01T00:00:00Z");
        insert(OTHER_OWNER, "Other Name", "https://img.test/other.png",
                "other@example.test", null);
    }

    protected void insert(
            String id, String displayName, String image,
            String recoveryEmail, String verifiedAt) {
        jdbc.update("""
                insert into users(
                  id,display_name,email,email_verified,image,recovery_email,
                  recovery_email_verified_at)
                values (?,?,?,false,?,?,?::timestamptz)
                """, id, displayName, id + "@users.test", image, recoveryEmail, verifiedAt);
    }
}
