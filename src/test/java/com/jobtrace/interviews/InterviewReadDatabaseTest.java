package com.jobtrace.interviews;

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

public abstract class InterviewReadDatabaseTest extends PostgresIntegrationTest {

    protected static final String OWNER = "owner-a";
    protected static final String OTHER_OWNER = "owner-b";
    protected static final String APPLICATION_ID = "00000000-0000-0000-0000-000000000201";
    protected static final String OTHER_APPLICATION_ID = "00000000-0000-0000-0000-000000000202";
    protected static final String R101 = "00000000-0000-0000-0000-000000000101";
    protected static final String R102 = "00000000-0000-0000-0000-000000000102";
    protected static final String R103 = "00000000-0000-0000-0000-000000000103";
    protected static final String R201 = "00000000-0000-0000-0000-000000000401";

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
    void seedInterviews() {
        jdbc.execute("""
                drop table if exists interview_action_items, interview_questions,
                  interview_reviews, application_events, application_stage_occurrences,
                  applications cascade
                """);
        jdbc.execute("""
                drop type if exists interview_author_mode, interview_visibility,
                  question_category, review_status, round_result, interview_format,
                  application_event_type, recruitment_stage, application_type,
                  application_status cascade
                """);
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/interviews-read-model.sql")).execute(dataSource);
        jdbc.update("""
                insert into applications (id,owner_id,company_name,position_name,
                  applied_date,type,status,latest_date,version,created_at,updated_at) values
                (?::uuid,?,'Example Labs','Backend Engineer','2026-09-01',
                  'campus_recruitment','submitted','2026-09-20',1,
                  '2026-09-01T00:00:00Z','2026-09-20T00:00:00Z'),
                ('00000000-0000-0000-0000-000000000203',?,'Other Corp','Designer',
                  '2026-09-01','social_recruitment','submitted','2026-09-20',1,
                  '2026-09-01T00:00:00Z','2026-09-20T00:00:00Z'),
                (?::uuid,?,'Secret Labs','Backend Engineer','2026-09-01',
                  'campus_recruitment','submitted','2026-09-20',1,
                  '2026-09-01T00:00:00Z','2026-09-20T00:00:00Z')
                """, APPLICATION_ID, OWNER, OWNER, OTHER_APPLICATION_ID, OTHER_OWNER);
        jdbc.update("""
                insert into application_stage_occurrences
                  (id,application_id,stage,occurred_on,created_at) values
                ('00000000-0000-0000-0000-000000000301',?::uuid,
                  'interview_1','2026-09-18','2026-09-18T00:00:00Z')
                """, APPLICATION_ID);
        jdbc.update("""
                insert into interview_reviews
                  (id,owner_id,application_id,stage_occurrence_id,stage_snapshot,
                   interviewed_on,round_result,status,visibility,author_mode,published_at,
                   version,created_at,updated_at) values
                (?::uuid,?,?::uuid,'00000000-0000-0000-0000-000000000301',
                  'interview_1','2026-09-18','pending','pending_review','private',
                  'anonymous',null,1,'2026-09-18T00:00:00Z','2026-09-18T00:00:00Z'),
                (?::uuid,?,?::uuid,null,'assessment','2026-09-20','passed',
                  'completed','public','anonymous','2026-09-21T08:00:00Z',1,
                  '2026-09-20T00:00:00Z','2026-09-21T08:00:00Z'),
                (?::uuid,?,'00000000-0000-0000-0000-000000000203',null,
                  'interview_2','2026-09-20','failed','draft','public','attributed',
                  '2026-09-21T09:00:00Z',1,'2026-09-20T00:00:00Z','2026-09-21T09:00:00Z'),
                (?::uuid,?,?::uuid,null,'assessment','2026-09-20','passed',
                  'completed','public','anonymous','2026-09-21T08:00:00Z',1,
                  '2026-09-20T00:00:00Z','2026-09-21T08:00:00Z')
                """, R101, OWNER, APPLICATION_ID, R102, OWNER, APPLICATION_ID,
                R103, OWNER, R201, OTHER_OWNER, OTHER_APPLICATION_ID);
        jdbc.update("""
                insert into interview_questions
                  (id,interview_review_id,sort_order,category,question) values
                ('00000000-0000-0000-0000-000000000501',?::uuid,0,'technical','Java concurrency'),
                ('00000000-0000-0000-0000-000000000502',?::uuid,0,'project','SQL indexing'),
                ('00000000-0000-0000-0000-000000000503',?::uuid,1,'behavioral','Teamwork'),
                ('00000000-0000-0000-0000-000000000504',?::uuid,0,'technical','Secret question')
                """, R101, R102, R102, R201);
        jdbc.update("""
                insert into interview_action_items
                  (id,interview_review_id,sort_order,content,completed) values
                ('00000000-0000-0000-0000-000000000601',?::uuid,0,'Review indexes',false)
                """, R102);
    }
}
