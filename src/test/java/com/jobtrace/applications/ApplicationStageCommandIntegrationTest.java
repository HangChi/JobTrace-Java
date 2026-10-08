package com.jobtrace.applications;

import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationStageCommandIntegrationTest extends ApplicationReadDatabaseTest {

    private static final String OCCURRENCE = "20000000-0000-4000-8000-000000000002";

    @Autowired MockMvc mvc;
    @BeforeEach
    void installStageFunctions() throws Exception {
        var resource = new ClassPathResource("postgres/application-stage-commands.sql");
        try (var stream = resource.getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void addsEditsAndRemovesAnOwnedStage() throws Exception {
        mvc.perform(post("/api/applications/{id}/stages", FIRST_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"interview_1\",\"occurredOn\":\"2026-09-20\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(4));
        mvc.perform(patch("/api/applications/{id}/stages/{occurrenceId}", FIRST_ID, OCCURRENCE)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"interview_2","occurredOn":"2026-09-16",
                                 "changeDate":"2026-09-21","version":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5));
        assertThat(jdbc.queryForObject(
                "select stage::text from application_stage_occurrences where id=?::uuid",
                String.class, OCCURRENCE)).isEqualTo("interview_2");
        mvc.perform(delete("/api/applications/{id}/stages/{occurrenceId}", FIRST_ID, OCCURRENCE)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeDate\":\"2026-09-22\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6));
    }

    @Test
    void rejectsDuplicateInvalidTerminalAndForeignStages() throws Exception {
        mvc.perform(post("/api/applications/{id}/stages", FIRST_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"screening\",\"occurredOn\":\"2026-09-10\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/applications/{id}/stages", FIRST_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"interview_1\",\"occurredOn\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/applications/{id}/stages", SECOND_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"interview_1\",\"occurredOn\":\"2026-09-10\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/applications/{id}/stages/{occurrenceId}", OTHER_ID,
                        "20000000-0000-4000-8000-000000000003")
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"interview_1","occurredOn":"2026-09-20",
                                 "changeDate":"2026-09-21","version":1}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/applications/{id}/stages/{occurrenceId}", FIRST_ID, OCCURRENCE)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"interview_2","occurredOn":"2026-09-16",
                                 "changeDate":"2026-09-21","version":2}
                                """))
                .andExpect(status().isConflict());
    }
}
