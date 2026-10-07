package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReminderLifecycleIntegrationTest extends ReminderOperationsDatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void completesThenReopensAndRejectsIllegalReplay() throws Exception {
        mvc.perform(post("/api/reminders/{id}/complete", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mvc.perform(post("/api/reminders/{id}/complete", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/reminders/{id}/reopen", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":2,"eventAt":"2030-10-03T12:00:00Z",
                                 "notifyAt":"2030-10-03T10:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(3));
        assertThat(jdbc.queryForObject("select version from scheduled_reminders where id=?::uuid",
                Integer.class, R101)).isEqualTo(3);
    }

    @Test
    void snoozeOwnedOnlyAndKeepsReminderActive() throws Exception {
        mvc.perform(post("/api/reminders/{id}/snooze", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":1,"notifyAt":"2030-10-04T13:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(post("/api/reminders/{id}/snooze", R201)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":1,"notifyAt":"2030-10-04T13:00:00Z"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/reminders/{id}/snooze", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":1,"notifyAt":"2030-10-05T13:00:00Z"}
                                """))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select version from scheduled_reminders where id=?::uuid",
                Integer.class, R201)).isEqualTo(1);
    }
}
