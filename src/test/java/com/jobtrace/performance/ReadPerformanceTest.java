package com.jobtrace.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.analytics.application.GetAnalyticsSummary;
import com.jobtrace.identityaccess.BridgeTokenFixtures;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.identityaccess.web.BridgeTokenVerifier;
import com.jobtrace.shared.health.HealthController;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class ReadPerformanceTest extends PostgresIntegrationTest {

    private static final Duration READ_P95_BUDGET = Duration.ofMillis(500);
    private static final int WARM_UP_ITERATIONS = 5;
    private static final int SAMPLE_ITERATIONS = 30;

    @Autowired
    private HealthController healthController;

    @Autowired
    private GetAnalyticsSummary getAnalyticsSummary;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createRepresentativeEmptySchema() {
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
    }

    @Test
    void healthReadsStayWithinTheDefaultP95Budget() {
        assertP95WithinBudget(() -> {
            healthController.live();
            healthController.ready();
        });
    }

    @Test
    void analyticsReadsStayWithinTheDefaultP95Budget() {
        assertP95WithinBudget(() -> getAnalyticsSummary.execute("performance-owner"));
    }

    @Test
    void bridgeValidationAndReplayClaimStayWithinTheDefaultP95Budget() {
        var properties = BridgeTokenFixtures.jobTraceProperties();
        var verifier = new BridgeTokenVerifier(
                properties,
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC),
                JsonMapper.builder().build());
        ReplayGuard replayGuard = new ReplayGuard() {
            @Override
            public boolean claim(String issuer, String tokenId, Instant retainUntil) {
                return true;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        var claimAssertion = new ClaimAssertionUseCase(
                verifier,
                replayGuard,
                properties.authBridge());
        String token = BridgeTokenFixtures.validToken("performance-owner");

        assertP95WithinBudget(() -> claimAssertion.execute(
                token,
                "GET",
                "/api/analytics/summary",
                BridgeTokenFixtures.REQUEST_ID));
    }

    private static void assertP95WithinBudget(Runnable operation) {
        for (int iteration = 0; iteration < WARM_UP_ITERATIONS; iteration++) {
            operation.run();
        }

        List<Long> samples = new ArrayList<>(SAMPLE_ITERATIONS);
        for (int iteration = 0; iteration < SAMPLE_ITERATIONS; iteration++) {
            long startedAt = System.nanoTime();
            operation.run();
            samples.add(System.nanoTime() - startedAt);
        }

        Collections.sort(samples);
        int percentileIndex = (int) Math.ceil(SAMPLE_ITERATIONS * 0.95) - 1;
        Duration p95 = Duration.ofNanos(samples.get(percentileIndex));

        assertThat(p95)
                .as("representative read p95")
                .isLessThanOrEqualTo(READ_P95_BUDGET);
    }
}
