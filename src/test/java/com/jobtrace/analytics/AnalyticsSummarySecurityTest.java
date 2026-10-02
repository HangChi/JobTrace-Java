package com.jobtrace.analytics;

import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static com.jobtrace.migration.ContractComparisonTest.loadFixture;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrace.identityaccess.BridgeTokenFixtures;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.shared.health.BridgeReplayAvailability;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AnalyticsSummarySecurityTest.FixedClockConfiguration.class)
class AnalyticsSummarySecurityTest extends PostgresIntegrationTest {

    private static final String OWNER_A = "owner-a";
    private static final String OWNER_B = "owner-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seedLegacyAnalyticsSchema() {
        jdbcTemplate.execute("""
                drop table if exists progress_reminder_completions;
                drop table if exists interview_reviews;
                drop table if exists application_stage_occurrences;
                drop table if exists applications;

                create table applications (
                    id uuid primary key,
                    owner_id text not null,
                    company_name text not null,
                    position_name text not null,
                    city text,
                    job_url text,
                    applied_date date not null,
                    type text not null,
                    status text not null,
                    latest_date date not null,
                    version integer not null
                );
                create table application_stage_occurrences (
                    id uuid primary key,
                    application_id uuid not null references applications(id) on delete cascade,
                    stage text not null,
                    occurred_on date not null,
                    created_at timestamptz not null default now()
                );
                create table interview_reviews (
                    id uuid primary key,
                    owner_id text not null,
                    application_id uuid not null references applications(id) on delete cascade,
                    stage_occurrence_id uuid references application_stage_occurrences(id),
                    status text not null
                );
                create table progress_reminder_completions (
                    id uuid primary key,
                    owner_id text not null,
                    stage_occurrence_id uuid not null references application_stage_occurrences(id)
                );
                """);
        jdbcTemplate.execute("""
                insert into applications values
                  ('00000000-0000-0000-0000-0000000000a1', 'owner-a', 'Legacy Alpha', 'Engineer',
                   'Shanghai', 'https://example.test/alpha', '2026-08-01', 'campus_recruitment',
                   'submitted', '2026-08-01', 1),
                  ('00000000-0000-0000-0000-0000000000a2', 'owner-a', 'Legacy Beta', 'Engineer',
                   'Beijing', null, '2026-09-29', 'social_recruitment', 'offer', '2026-09-29', 1),
                  ('00000000-0000-0000-0000-0000000000a3', 'owner-a', 'Legacy Gamma', 'Engineer',
                   null, null, '2026-09-01', 'daily_internship', 'refused', '2026-09-01', 1),
                  ('00000000-0000-0000-0000-0000000000a4', 'owner-a', 'Legacy Delta', 'Intern',
                   null, null, '2026-08-10', 'summer_internship', 'submitted', '2026-08-10', 2),
                  ('00000000-0000-0000-0000-0000000000e1', 'owner-b', 'Other Owner', 'Engineer',
                   null, null, '2026-09-30', 'campus_recruitment', 'offer', '2026-09-30', 1);

                insert into application_stage_occurrences(id, application_id, stage, occurred_on) values
                  ('00000000-0000-0000-0000-0000000000b1',
                   '00000000-0000-0000-0000-0000000000a1', 'screening', '2026-08-05'),
                  ('00000000-0000-0000-0000-0000000000b2',
                   '00000000-0000-0000-0000-0000000000a2', 'final_interview', '2026-09-29'),
                  ('00000000-0000-0000-0000-0000000000b4',
                   '00000000-0000-0000-0000-0000000000a4', 'assessment', '2026-08-20');
                """);
    }

    @Test
    void rejectsAnonymousAndUntrustedIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/analytics/summary").header("x-user-id", OWNER_A))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCapturedLegacyContractForTheAuthenticatedOwner() throws Exception {
        String response = mockMvc.perform(get("/api/analytics/summary")
                        .with(user(OWNER_A))
                        .header("x-user-id", OWNER_B))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquivalent(loadFixture("representative.legacy.json"), objectMapper.readTree(response));
    }

    @Test
    void returnsTheEmptyLegacyContractForAnOwnerWithoutApplications() throws Exception {
        String response = mockMvc.perform(get("/api/analytics/summary").with(user("owner-empty")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquivalent(loadFixture("empty.legacy.json"), objectMapper.readTree(response));
    }

    @Test
    void neverIncludesAnotherOwnersCounts() throws Exception {
        mockMvc.perform(get("/api/analytics/summary").with(user(OWNER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.submitted").value(0))
                .andExpect(jsonPath("$.offers").value(1))
                .andExpect(jsonPath("$.followUps").isEmpty())
                .andExpect(jsonPath("$.progressReminders").isEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-30T04:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@Import(AnalyticsSummaryBridgeSecurityTest.BridgeTestConfiguration.class)
class AnalyticsSummaryBridgeSecurityTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @BeforeEach
    void seedTwoOwners() {
        jdbcTemplate.execute("""
                drop table if exists progress_reminder_completions;
                drop table if exists interview_reviews;
                drop table if exists application_stage_occurrences;
                drop table if exists applications;

                create table applications (
                    id uuid primary key,
                    owner_id text not null,
                    company_name text not null,
                    position_name text not null,
                    city text,
                    job_url text,
                    applied_date date not null,
                    type text not null,
                    status text not null,
                    latest_date date not null,
                    version integer not null
                );
                create table application_stage_occurrences (
                    id uuid primary key,
                    application_id uuid not null references applications(id) on delete cascade,
                    stage text not null,
                    occurred_on date not null,
                    created_at timestamptz not null default now()
                );
                create table interview_reviews (
                    id uuid primary key,
                    owner_id text not null,
                    application_id uuid not null references applications(id) on delete cascade,
                    stage_occurrence_id uuid references application_stage_occurrences(id),
                    status text not null
                );
                create table progress_reminder_completions (
                    id uuid primary key,
                    owner_id text not null,
                    stage_occurrence_id uuid not null references application_stage_occurrences(id)
                );
                insert into applications values
                  ('00000000-0000-0000-0000-0000000000a1', 'owner-a', 'Alpha', 'Engineer',
                   null, null, '2026-09-30', 'campus_recruitment', 'submitted', '2026-09-30', 1),
                  ('00000000-0000-0000-0000-0000000000b1', 'owner-b', 'Beta', 'Engineer',
                   null, null, '2026-09-30', 'campus_recruitment', 'offer', '2026-09-30', 1);
                """);
    }

    @Test
    void signedBridgePrincipalKeepsAnalyticsOwnerIsolated() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                        .header(
                                "Authorization",
                                "JobTraceBridge " + BridgeTokenFixtures.validToken("owner-a"))
                        .header("x-user-id", "owner-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.submitted").value(1))
                .andExpect(jsonPath("$.offers").value(0));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BridgeTestConfiguration {

        @Bean
        @Primary
        Clock bridgeClock() {
            return Clock.fixed(BridgeTokenFixtures.NOW, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        TestReplayGuard bridgeReplayGuard() {
            return new TestReplayGuard();
        }

        static final class TestReplayGuard implements ReplayGuard, BridgeReplayAvailability {
                private final Set<String> claimed = new HashSet<>();

            @Override
            public boolean claim(String issuer, String tokenId, Instant retainUntil) {
                return claimed.add(issuer + ":" + tokenId);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        }
    }
}
