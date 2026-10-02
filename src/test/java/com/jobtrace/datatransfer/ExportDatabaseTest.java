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

abstract class ExportDatabaseTest extends PostgresIntegrationTest {

    static final String OWNER = "owner-a";
    static final String OTHER_OWNER = "owner-b";
    static final String A101 = "00000000-0000-0000-0000-000000000101";
    static final String A102 = "00000000-0000-0000-0000-000000000102";
    static final String A201 = "00000000-0000-0000-0000-000000000201";
    static final String R301 = "00000000-0000-0000-0000-000000000301";
    static final String R302 = "00000000-0000-0000-0000-000000000302";
    static final String R401 = "00000000-0000-0000-0000-000000000401";

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
    void seedExports() {
        jdbc.execute("drop table if exists interview_action_items, interview_questions, "
                + "interview_reviews, application_stage_occurrences, applications cascade");
        jdbc.execute("drop type if exists interview_author_mode, interview_visibility, "
                + "question_category, review_status, round_result, interview_format, "
                + "recruitment_stage, application_type, application_status cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/data-export-read-model.sql")).execute(dataSource);
        jdbc.update("""
                insert into applications (id,owner_id,company_name,position_name,city,job_url,
                  applied_date,type,status,notes,latest_date,version,created_at,updated_at) values
                (?::uuid,'owner-a','示例,科技','后端"工程师','上海',
                  'https://example.test/jobs/101','2026-09-30','spring_recruitment',
                  'submitted',E'=2+3\\n第二行','2026-10-01',1,
                  '2026-09-30T01:02:03Z','2026-10-01T01:02:03Z'),
                (?::uuid,'owner-a','第二公司','设计师','北京','javascript:alert(1)',
                  '2026-09-20','social_recruitment','offer',null,'2026-09-20',1,
                  '2026-09-20T01:00:00Z','2026-09-20T01:00:00Z'),
                (?::uuid,'owner-b','不可见公司','秘密岗位',null,null,'2026-10-01',
                  'campus_recruitment','submitted','private','2026-10-01',1,
                  '2026-10-01T01:00:00Z','2026-10-01T01:00:00Z')
                """, A101, A102, A201);
        jdbc.update("""
                insert into application_stage_occurrences
                  (id,application_id,stage,occurred_on,created_at) values
                ('00000000-0000-0000-0000-000000000501',?::uuid,'interview_1',
                  '2026-10-01','2026-10-01T01:00:00Z'),
                ('00000000-0000-0000-0000-000000000502',?::uuid,'screening',
                  '2026-09-30','2026-09-30T01:00:00Z'),
                ('00000000-0000-0000-0000-000000000503',?::uuid,'final_interview',
                  '2026-10-01','2026-10-01T01:00:00Z')
                """, A101, A101, A201);
        jdbc.update("""
                insert into interview_reviews(id,owner_id,application_id,stage_occurrence_id,
                  stage_snapshot,interviewed_on,format,duration_minutes,round_result,
                  status,visibility,author_mode,version,created_at,updated_at) values
                (?::uuid,'owner-a',?::uuid,null,'interview_1','2026-10-01','online',45,
                  'passed','completed','private','anonymous',1,now(),now()),
                (?::uuid,'owner-a',?::uuid,null,'interview_1','2026-10-01','online',45,
                  'passed','completed','private','anonymous',1,now(),now()),
                (?::uuid,'owner-b',?::uuid,null,'interview_2','2026-10-01','online',45,
                  'passed','completed','private','anonymous',1,now(),now())
                """, R301, A101, R302, A101, R401, A201);
        jdbc.update("""
                insert into interview_questions(id,interview_review_id,sort_order,category,
                  question) values
                ('00000000-0000-0000-0000-000000000601',?::uuid,0,'other',
                  E'# 一面复盘\\n\\n面经正文'),
                ('00000000-0000-0000-0000-000000000602',?::uuid,0,'other',
                  '第二篇复盘'),
                ('00000000-0000-0000-0000-000000000603',?::uuid,0,'other',
                  '绝不可见')
                """, R301, R302, R401);
    }
}
