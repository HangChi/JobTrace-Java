package com.jobtrace.reminders;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public abstract class ReminderReadDatabaseTest extends PostgresIntegrationTest {

    protected static final Instant READ_INSTANT = Instant.parse("2026-10-02T00:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    public static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock reminderTestClock() {
            return Clock.fixed(READ_INSTANT, ZoneOffset.UTC);
        }
    }

    protected static final String OWNER = "owner-a";
    protected static final String OTHER_OWNER = "owner-b";
    protected static final String EMPTY_OWNER = "owner-c";
    protected static final String R101 = "00000000-0000-0000-0000-000000000101";
    protected static final String R102 = "00000000-0000-0000-0000-000000000102";
    protected static final String R103 = "00000000-0000-0000-0000-000000000103";
    protected static final String R104 = "00000000-0000-0000-0000-000000000104";
    protected static final String R105 = "00000000-0000-0000-0000-000000000105";
    protected static final String R201 = "00000000-0000-0000-0000-000000000201";

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
    void seedReminders() {
        jdbc.execute("drop table if exists reminder_notification_attempts, scheduled_reminders, "
                + "applications, users cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/reminders-read-model.sql")).execute(dataSource);
        jdbc.update("""
                insert into users(id,recovery_email,recovery_email_verified_at,
                  reminder_home_enabled,reminder_home_view,reminder_default_lead,
                  reminder_default_snooze_minutes,reminder_email_default) values
                ('owner-a','owner@example.test','2026-09-01T00:00:00Z',false,'suggestions','30m',60,true),
                ('owner-b','hidden@example.test',null,true,'scheduled','1h',30,false),
                ('owner-c',null,null,true,'scheduled','1h',30,false)
                """);
        jdbc.update("""
                insert into applications(id,owner_id,company_name,position_name) values
                ('00000000-0000-0000-0000-000000000201','owner-a','Example Labs','Backend Engineer'),
                ('00000000-0000-0000-0000-000000000202','owner-b','Secret Labs','Designer')
                """);
        jdbc.update("""
                insert into scheduled_reminders(id,owner_id,application_id,title,
                  event_at,notify_at,email_enabled,status,version,completed_at,cancelled_at) values
                (?::uuid,'owner-a','00000000-0000-0000-0000-000000000201',
                  'Follow up','2026-10-03T00:00:00Z','2026-10-02T00:00:00Z',true,'pending',1,null,null),
                (?::uuid,'owner-a','00000000-0000-0000-0000-000000000201',
                  'Prepare interview','2026-10-04T00:00:00Z','2026-10-03T00:00:00Z',false,'pending',2,null,null),
                (?::uuid,'owner-a','00000000-0000-0000-0000-000000000201',
                  'Send thank-you note','2026-09-20T00:00:00Z','2026-09-19T00:00:00Z',false,
                  'completed',2,'2026-09-20T01:00:00Z',null),
                (?::uuid,'owner-a','00000000-0000-0000-0000-000000000201',
                  'Old plan','2026-09-22T00:00:00Z','2026-09-21T00:00:00Z',false,
                  'cancelled',3,null,'2026-09-21T02:00:00Z'),
                (?::uuid,'owner-a','00000000-0000-0000-0000-000000000202',
                  'Inconsistent secret','2026-10-05T00:00:00Z','2026-10-04T00:00:00Z',false,
                  'pending',1,null,null),
                (?::uuid,'owner-b','00000000-0000-0000-0000-000000000202',
                  'Secret follow up','2026-10-04T00:00:00Z','2026-10-03T00:00:00Z',true,
                  'pending',1,null,null)
                """, R101, R102, R103, R104, R105, R201);
        jdbc.update("""
                insert into reminder_notification_attempts
                  (id,reminder_id,owner_id,channel,scheduled_for,status,updated_at) values
                ('00000000-0000-0000-0000-000000000301',?::uuid,'owner-a','email',
                  '2026-10-02T00:00:00Z','failed','2026-10-02T00:01:00Z'),
                ('00000000-0000-0000-0000-000000000302',?::uuid,'owner-a','email',
                  '2026-10-01T00:00:00Z','sent','2026-10-01T00:01:00Z'),
                ('00000000-0000-0000-0000-000000000303',?::uuid,'owner-b','email',
                  '2026-10-03T00:00:00Z','sent','2026-10-03T00:01:00Z')
                """, R101, R102, R201);
    }
}
