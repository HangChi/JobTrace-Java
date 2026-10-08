package com.jobtrace.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AnalyticsReportIntegrationTest.FixedClock.class)
class AnalyticsReportIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("""
                drop table if exists progress_reminder_completions, interview_reviews,
                  application_events, application_stage_occurrences, applications cascade;
                create table applications(id uuid primary key, owner_id text not null,
                  company_name text not null, position_name text not null, city text,
                  applied_date date not null, type text not null, status text not null);
                create table application_stage_occurrences(id uuid primary key,
                  application_id uuid not null references applications(id),
                  stage text not null, occurred_on date not null);
                create table interview_reviews(id uuid primary key, owner_id text not null,
                  application_id uuid not null references applications(id),
                  stage_snapshot text not null, status text not null, round_result text not null);
                insert into applications values
                  ('10000000-0000-4000-8000-000000000001','owner-a','A','Engineer',
                   '上海','2026-09-20','campus_recruitment','offer'),
                  ('10000000-0000-4000-8000-000000000002','owner-a','B','Designer',
                   '北京','2026-09-22','social_recruitment','submitted'),
                  ('10000000-0000-4000-8000-000000000003','owner-a','C','Engineer',
                   null,'2026-08-20','campus_recruitment','refused'),
                  ('10000000-0000-4000-8000-000000000004','owner-b','Private','Engineer',
                   '深圳','2026-09-23','campus_recruitment','offer');
                insert into application_stage_occurrences values
                  ('20000000-0000-4000-8000-000000000001',
                   '10000000-0000-4000-8000-000000000001','interview_1','2026-09-25'),
                  ('20000000-0000-4000-8000-000000000002',
                   '10000000-0000-4000-8000-000000000001','final_interview','2026-09-29'),
                  ('20000000-0000-4000-8000-000000000003',
                   '10000000-0000-4000-8000-000000000004','final_interview','2026-09-29');
                insert into interview_reviews values
                  ('30000000-0000-4000-8000-000000000001','owner-a',
                   '10000000-0000-4000-8000-000000000001','final_interview','completed','passed'),
                  ('30000000-0000-4000-8000-000000000002','owner-b',
                   '10000000-0000-4000-8000-000000000004','final_interview','completed','passed');
                """);
    }

    @Test
    void reportsOwnedCohortWithoutCaching() throws Exception {
        mvc.perform(get("/api/analytics/report?period=90d").with(owner("owner-a")))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "no-store"))
                .andExpect(jsonPath("$.metrics.applications.value").value(3))
                .andExpect(jsonPath("$.metrics.interviewRate.value").value(33.3))
                .andExpect(jsonPath("$.metrics.offerRate.value").value(33.3))
                .andExpect(jsonPath("$.milestones[2].count").value(1))
                .andExpect(jsonPath("$.interviews.total").value(1))
                .andExpect(jsonPath("$.availableCities.length()").value(2))
                .andExpect(jsonPath("$.sampleSufficient").value(false));
    }

    @Test
    void filtersByTypeAndCityAndReturnsEmptyCohort() throws Exception {
        mvc.perform(get("/api/analytics/report?period=30d&type=campus_recruitment&city=上海")
                        .with(owner("owner-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.applications.value").value(1))
                .andExpect(jsonPath("$.metrics.offerRate.value").value(100.0));
        mvc.perform(get("/api/analytics/report?period=all").with(owner("empty-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.applications.value").value(0))
                .andExpect(jsonPath("$.trend").isEmpty())
                .andExpect(jsonPath("$.stageReach.length()").value(8));
    }

    @Test
    void rejectsBadRangeBeforeQueryAndAnonymousAccess() throws Exception {
        mvc.perform(get("/api/analytics/report?period=custom&from=2026-10-04&to=2026-10-06")
                        .with(owner("owner-a")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        mvc.perform(get("/api/analytics/report").header("x-user-id", "owner-a"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from applications", Integer.class)).isEqualTo(4);
    }

    @Test
    void reportMeetsLocalReadBudget() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/analytics/report?period=90d").with(owner("owner-a")))
                    .andExpect(status().isOk());
        }
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) {
            long started = System.nanoTime();
            mvc.perform(get("/api/analytics/report?period=90d").with(owner("owner-a")))
                    .andExpect(status().isOk());
            samples.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(samples);
        long p95 = samples.get(18);
        System.out.println("023 analytics report p95 ms: " + p95 + "; fixture applications=4");
        assertThat(p95).isLessThanOrEqualTo(500);
    }

    private static RequestPostProcessor owner(String ownerId) {
        var auth = new UsernamePasswordAuthenticationToken(ownerId, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new BridgeIdentity(ownerId, BridgeIdentity.Role.USER, 3));
        return authentication(auth);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary Clock reportClock() {
            return Clock.fixed(Instant.parse("2026-10-05T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
