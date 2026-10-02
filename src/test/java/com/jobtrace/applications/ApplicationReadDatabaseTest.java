package com.jobtrace.applications;

import com.jobtrace.testing.PostgresIntegrationTest;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
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
import java.util.List;

abstract class ApplicationReadDatabaseTest extends PostgresIntegrationTest {

    static final String OWNER = "owner-a";
    static final String OTHER_OWNER = "owner-b";
    static final String FIRST_ID = "10000000-0000-4000-8000-000000000001";
    static final String SECOND_ID = "10000000-0000-4000-8000-000000000002";
    static final String OTHER_ID = "10000000-0000-4000-8000-000000000003";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    static RequestPostProcessor asOwner(String ownerId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                ownerId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new BridgeIdentity(ownerId, BridgeIdentity.Role.USER, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    @BeforeEach
    void seedApplications() {
        jdbc.execute("drop table if exists application_events, application_stage_occurrences, applications cascade");
        jdbc.execute("""
                drop type if exists application_event_type, recruitment_stage,
                  application_type, application_status cascade
                """);
        new ResourceDatabasePopulator(new ClassPathResource("postgres/applications-read-model.sql"))
                .execute(dataSource);
        jdbc.update("""
                insert into applications (
                  id, owner_id, company_name, position_name, city, job_url, applied_date,
                  type, status, notes, latest_date, version, created_at, updated_at
                ) values
                  (?::uuid, ?, 'Contract Company', 'Platform Engineer', '上海',
                   'https://jobs.example.test/platform-engineer', '2026-09-01',
                   'campus_recruitment', 'submitted', 'Synthetic contract note',
                   '2026-09-10', 3, '2026-09-01T01:00:00Z', '2026-09-15T01:00:00Z'),
                  (?::uuid, ?, 'Second Company', 'Designer', '北京', null, '2026-09-05',
                   'social_recruitment', 'offer', null, '2026-09-11', 1,
                   '2026-09-05T01:00:00Z', '2026-09-11T01:00:00Z'),
                  (?::uuid, ?, 'Private Company', 'Engineer', null, null, '2026-09-02',
                   'campus_recruitment', 'submitted', 'private',
                   '2026-09-30', 1, '2026-09-02T01:00:00Z', '2026-09-30T01:00:00Z')
                """, FIRST_ID, OWNER, SECOND_ID, OWNER, OTHER_ID, OTHER_OWNER);
        jdbc.update("""
                insert into application_stage_occurrences
                  (id, application_id, stage, occurred_on, created_at) values
                  ('20000000-0000-4000-8000-000000000001', ?::uuid,
                   'screening', '2026-09-01', '2026-09-01T01:00:00Z'),
                  ('20000000-0000-4000-8000-000000000002', ?::uuid,
                   'assessment', '2026-09-15', '2026-09-15T01:00:00Z'),
                  ('20000000-0000-4000-8000-000000000003', ?::uuid,
                   'final_interview', '2026-09-30', '2026-09-30T01:00:00Z')
                """, FIRST_ID, FIRST_ID, OTHER_ID);
        jdbc.update("""
                insert into application_events
                  (id, application_id, type, occurred_on, before, after, created_at) values
                  ('30000000-0000-4000-8000-000000000001', ?::uuid,
                   'created', '2026-09-01', null, '{"status":"submitted"}',
                   '2026-09-01T01:00:00Z'),
                  ('30000000-0000-4000-8000-000000000002', ?::uuid,
                   'stage_added', '2026-09-15', null, '{"stage":"assessment"}',
                   '2026-09-15T01:00:00Z'),
                  ('30000000-0000-4000-8000-000000000003', ?::uuid,
                   'created', '2026-09-02', null, '{"status":"submitted"}',
                   '2026-09-02T01:00:00Z')
                """, FIRST_ID, FIRST_ID, OTHER_ID);
    }
}
