package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.admin.application.AdminReadQuery;
import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.testing.PostgresIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@SpringBootTest
class AdminReadQueryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected AdminReadQuery query;

    @BeforeEach
    protected void seed() {
        jdbc.execute("drop table if exists admin_audit_events, interview_action_items, "
                + "interview_questions, interview_reviews, application_stage_occurrences, "
                + "applications, sessions, users cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/admin-read-model.sql")).execute(dataSource);
        jdbc.update("""
                insert into users(id,username,display_username,email,recovery_email,role,disabled,
                  access_version,created_at) values
                ('admin-a','admin','Administrator','admin@internal.test',null,'admin',false,4,
                  now()-interval '40 days'),
                ('user-a','alice','Alice','alice@internal.test','alice@example.test','user',false,2,
                  now()-interval '2 days'),
                ('user-b',null,null,'bob@internal.test',null,'user',true,3,now()-interval '1 day')
                """);
        jdbc.update("insert into sessions(id,user_id,created_at) values "
                + "('session-a','user-a',now()-interval '1 day'),"
                + "('session-b','user-b',now()-interval '1 day')");
        jdbc.update("""
                insert into applications(id,owner_id,company_name,position_name,city,job_url,type,
                  status,applied_date,latest_date,notes) values
                ('application-a','user-a','Example','Engineer','Shanghai','https://example.test',
                  'campus','submitted',current_date-3,current_date-1,'private note')
                """);
        jdbc.update("""
                insert into application_stage_occurrences(id,application_id,stage,occurred_on,created_at)
                values ('stage-a','application-a','技术一面',current_date-2,now()-interval '2 days')
                """);
        jdbc.update("""
                insert into interview_reviews(id,owner_id,application_id,stage_occurrence_id,
                  stage_snapshot,interviewed_on,status,round_result,format,duration_minutes,
                  interviewer_notes,highlights,gaps) values
                ('interview-a','user-a','application-a','stage-a','技术一面',current_date-2,
                  'completed','passed','online',45,'notes','clear','none')
                """);
        jdbc.update("""
                insert into interview_questions(id,interview_review_id,category,question,
                  original_answer,follow_up_notes,improved_answer,self_rating,sort_order)
                values ('question-a','interview-a','Java','Explain records','answer',null,null,4,0)
                """);
        jdbc.update("""
                insert into interview_action_items(id,interview_review_id,content,completed,sort_order)
                values ('action-a','interview-a','Review concurrency',false,0)
                """);
        jdbc.update("""
                insert into admin_audit_events(id,request_id,actor_id,actor_identifier_snapshot,
                  target_user_id,target_identifier_snapshot,event_type,outcome,reason,before_data,
                  after_data,failure_code,created_at) values
                ('audit-a','request-a','admin-a','Administrator','user-a','Alice','disable_user',
                  'succeeded','Confirmed support request',
                  '{"role":"user","disabled":false,"accessVersion":1}',
                  '{"role":"user","disabled":true,"accessVersion":2}',null,now())
                """);
    }

    @Test
    void summaryUsesShanghaiWindowsAndGapFreeTrend() {
        assertThat(query.counts()).satisfies(counts -> {
            assertThat(counts.users()).isEqualTo(3);
            assertThat(counts.activeUsers()).isEqualTo(2);
            assertThat(counts.disabledUsers()).isEqualTo(1);
            assertThat(counts.administrators()).isEqualTo(1);
            assertThat(counts.applications()).isEqualTo(1);
            assertThat(counts.interviews()).isEqualTo(1);
        });
        assertThat(query.activity().windows().registered7d()).isEqualTo(2);
        assertThat(query.activity().windows().active7d()).isEqualTo(1);
        assertThat(query.activity().dailyTrend()).hasSize(30);
    }

    @Test
    void userReadsPreserveFiltersFallbacksAndCompleteDetail() {
        var criteria = new AdminQuery.Users("alice", AdminQuery.UserRole.USER,
                AdminQuery.UserStatus.ACTIVE, null, null, 1, 50);
        var page = query.users(criteria);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().username()).isEqualTo("Alice");
        assertThat(page.items().getFirst().email()).isEqualTo("alice@example.test");
        assertThat(page.items().getFirst().applicationCount()).isEqualTo(1);

        assertThat(query.user("user-b").orElseThrow().username()).isEqualTo("bob");
        assertThat(query.applications("user-a", 1).items().getFirst().stages())
                .extracting(stage -> stage.stage()).containsExactly("技术一面");
        assertThat(query.interviews("user-a", 1).items().getFirst()).satisfies(interview -> {
            assertThat(interview.stage()).isEqualTo("技术一面");
            assertThat(interview.questions()).hasSize(1);
            assertThat(interview.actionItems()).hasSize(1);
        });
    }

    @Test
    void auditFiltersAndMapsSnapshotsWithoutWriting() {
        int before = jdbc.queryForObject("select count(*) from admin_audit_events", Integer.class);
        var criteria = new AdminQuery.Audit("admin", "user-a",
                AdminQuery.EventType.DISABLE_USER, AdminQuery.Outcome.SUCCEEDED,
                null, null, 1, 50);
        var page = query.audit(criteria);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst()).satisfies(event -> {
            assertThat(event.actorIdentifier()).isEqualTo("Administrator");
            assertThat(event.before().accessVersion()).isEqualTo(1);
            assertThat(event.after().disabled()).isTrue();
        });
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_events", Integer.class))
                .isEqualTo(before);
    }
}
