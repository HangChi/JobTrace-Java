package com.jobtrace.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.analytics.application.ProgressReminderResolution;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ProgressReminderResolutionTest extends PostgresIntegrationTest {

    private static final String OWN_STAGE = "20000000-0000-4000-8000-000000000001";
    private static final String FOREIGN_STAGE = "20000000-0000-4000-8000-000000000002";
    private static final String TERMINAL_STAGE = "20000000-0000-4000-8000-000000000003";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProgressReminderResolution useCase;

    @BeforeEach
    void seed() {
        jdbc.execute("""
                drop table if exists progress_reminder_completions, interview_reviews,
                  application_events, application_stage_occurrences, applications cascade;
                create table applications(id uuid primary key, owner_id text not null, status text not null);
                create table application_stage_occurrences(id uuid primary key,
                  application_id uuid not null references applications(id));
                create table progress_reminder_completions(
                  id uuid primary key default gen_random_uuid(), owner_id text not null,
                  stage_occurrence_id uuid not null references application_stage_occurrences(id),
                  resolution text not null, reminder_id uuid, updated_at timestamptz default now(),
                  unique(owner_id, stage_occurrence_id));
                insert into applications values
                  ('10000000-0000-4000-8000-000000000001', 'owner-a', 'submitted'),
                  ('10000000-0000-4000-8000-000000000002', 'owner-b', 'submitted'),
                  ('10000000-0000-4000-8000-000000000003', 'owner-a', 'offer');
                insert into application_stage_occurrences values
                  ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001'),
                  ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002'),
                  ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003');
                """);
    }

    @Test
    void completesAndIdempotentlyChangesResolution() throws Exception {
        mvc.perform(post("/api/analytics/progress-reminders/{id}/complete", OWN_STAGE)
                        .with(owner("owner-a")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "no-store"))
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.resolution").value("completed"));
        mvc.perform(post("/api/analytics/progress-reminders/{id}/resolve", OWN_STAGE)
                        .with(owner("owner-a")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"dismissed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("dismissed"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("select count(*) from progress_reminder_completions", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals("dismissed",
                jdbc.queryForObject("select resolution from progress_reminder_completions", String.class));
    }

    @Test
    void hidesForeignMissingAndTerminalStages() throws Exception {
        for (String id : List.of(FOREIGN_STAGE, TERMINAL_STAGE,
                "20000000-0000-4000-8000-000000000099")) {
            mvc.perform(post("/api/analytics/progress-reminders/{id}/complete", id)
                            .with(owner("owner-a")).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("not_found"));
        }
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbc.queryForObject("select count(*) from progress_reminder_completions", Integer.class));
    }

    @Test
    void rejectsAnonymousAndInvalidResolution() throws Exception {
        mvc.perform(post("/api/analytics/progress-reminders/{id}/complete", OWN_STAGE)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/analytics/progress-reminders/{id}/resolve", OWN_STAGE)
                        .with(owner("owner-a")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"scheduled\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void useCaseRejectsInvalidArgumentsBeforeStorage() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> useCase.resolve("", UUID.fromString(OWN_STAGE),
                        ProgressReminderResolution.Resolution.COMPLETED));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> useCase.resolve("owner-a", null,
                        ProgressReminderResolution.Resolution.COMPLETED));
    }

    private static RequestPostProcessor owner(String ownerId) {
        var auth = new UsernamePasswordAuthenticationToken(ownerId, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new BridgeIdentity(ownerId, BridgeIdentity.Role.USER, 3));
        return authentication(auth);
    }
}
