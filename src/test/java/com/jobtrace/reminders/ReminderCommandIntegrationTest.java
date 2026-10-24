package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ReminderCommandIntegrationTest extends ReminderOperationsDatabaseTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createEditAndCancelOwnedStageReminder() throws Exception {
        String created = mvc.perform(post("/api/reminders").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","sourceStageOccurrenceId":"%s",
                                 "title":"  Prepare portfolio  ","eventAt":"2030-10-03T12:00:00+08:00",
                                 "notifyAt":"2030-10-03T10:00:00+08:00","emailEnabled":true}
                                """.formatted(APPLICATION_ID, STAGE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Prepare portfolio"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").textValue();
        assertThat(jdbc.queryForObject("""
                select resolution from progress_reminder_completions
                where owner_id=? and stage_occurrence_id=?::uuid
                """, String.class, OWNER, STAGE_ID)).isEqualTo("scheduled");
        mvc.perform(patch("/api/reminders/{id}", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","title":"Updated task",
                                 "eventAt":"2030-10-04T12:00:00+08:00",
                                 "notifyAt":"2030-10-04T10:00:00+08:00",
                                 "emailEnabled":false,"version":1}
                                """.formatted(APPLICATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(delete("/api/reminders/{id}", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{" +
                                "\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void foreignLinksAndStaleVersionsNeverWrite() throws Exception {
        for (String pair : new String[] {
                FOREIGN_APPLICATION_ID + ",null", APPLICATION_ID + ",\"" + FOREIGN_STAGE_ID + "\""}) {
            String[] values = pair.split(",", 2);
            mvc.perform(post("/api/reminders").with(asOwner(OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"applicationId":"%s","sourceStageOccurrenceId":%s,
                                     "title":"Not mine","eventAt":"2030-10-03T12:00:00Z",
                                     "notifyAt":"2030-10-03T10:00:00Z"}
                                    """.formatted(values[0], values[1])))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(patch("/api/reminders/{id}", R102).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","title":"Stale",
                                 "eventAt":"2030-10-03T12:00:00Z",
                                 "notifyAt":"2030-10-03T10:00:00Z",
                                 "emailEnabled":false,"version":1}
                                """.formatted(APPLICATION_ID)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select title from scheduled_reminders where id=?::uuid",
                String.class, R102)).isEqualTo("Prepare interview");
        assertThat(jdbc.queryForObject("select count(*) from progress_reminder_completions",
                Integer.class)).isZero();
    }

    @Test
    void rejectsMalformedJsonAndDoesNotTrustOwnerHeader() throws Exception {
        mvc.perform(post("/api/reminders").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reminders").with(asOwner(OTHER_OWNER)).with(csrf())
                        .header("x-owner-id", OWNER)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","title":"Cross-owner",
                                 "eventAt":"2030-10-03T12:00:00Z",
                                 "notifyAt":"2030-10-03T10:00:00Z"}
                                """.formatted(APPLICATION_ID)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from scheduled_reminders",
                Integer.class)).isEqualTo(6);
    }
}
