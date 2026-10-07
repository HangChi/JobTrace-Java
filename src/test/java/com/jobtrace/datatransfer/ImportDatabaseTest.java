package com.jobtrace.datatransfer;

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

abstract class ImportDatabaseTest extends PostgresIntegrationTest {

    static final String OWNER = "owner-a";
    static final String OTHER = "owner-b";

    @Autowired protected JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    static RequestPostProcessor asOwner(String id) {
        var authentication = new UsernamePasswordAuthenticationToken(
                id, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new BridgeIdentity(id, BridgeIdentity.Role.USER, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    @BeforeEach
    void seedImportSchema() {
        jdbc.execute("""
                drop table if exists import_rows,import_batches,users,
                  application_events,application_stage_occurrences,applications cascade
                """);
        jdbc.execute("""
                drop type if exists import_row_result,import_batch_status,
                  application_event_type,recruitment_stage,
                  application_type,application_status cascade
                """);
        new ResourceDatabasePopulator(new ClassPathResource("postgres/applications-read-model.sql"))
                .execute(dataSource);
        var importScript = new ResourceDatabasePopulator(
                new ClassPathResource("postgres/import-workflow.sql"));
        importScript.setSeparator("@@");
        importScript.execute(dataSource);
        jdbc.execute("""
                insert into applications(id,owner_id,company_name,position_name,
                  applied_date,type,status,latest_date,version,created_at,updated_at) values
                ('70000000-0000-4000-8000-000000000001','owner-a','Existing Co',
                  'Engineer','2026-08-13','campus_recruitment','submitted',
                  '2026-08-13',1,now(),now()),
                ('70000000-0000-4000-8000-000000000002','owner-b','Private Co',
                  'Engineer','2026-08-13','campus_recruitment','submitted',
                  '2026-08-13',1,now(),now())
                """);
    }
}
