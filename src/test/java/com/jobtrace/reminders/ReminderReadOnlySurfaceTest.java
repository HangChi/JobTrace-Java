package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.reminders.web.ReminderReadController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
class ReminderReadOnlySurfaceTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void onlyTwoGetHandlersAreMappedAndReadsLeaveRowsUnchanged() throws Exception {
        var methods = Arrays.stream(ReminderReadController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(RequestMapping.class)
                        || method.isAnnotationPresent(GetMapping.class))
                .toList();
        assertThat(methods).hasSize(2);
        assertThat(methods).allMatch(method -> method.isAnnotationPresent(GetMapping.class));
        assertThat(methods.stream().flatMap(method -> Arrays.stream(
                method.getAnnotation(GetMapping.class).value())).toList())
                .containsExactlyInAnyOrder("/api/reminders", "/api/reminder-settings");
        int remindersBefore = jdbc.queryForObject(
                "select count(*) from scheduled_reminders", Integer.class);
        int attemptsBefore = jdbc.queryForObject(
                "select count(*) from reminder_notification_attempts", Integer.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/reminders").with(asOwner(OWNER)))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/reminder-settings").with(asOwner(OWNER)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from scheduled_reminders", Integer.class))
                .isEqualTo(remindersBefore);
        assertThat(jdbc.queryForObject(
                "select count(*) from reminder_notification_attempts", Integer.class))
                .isEqualTo(attemptsBefore);
        mockMvc.perform(post("/api/reminders").with(asOwner(OWNER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/reminder-settings").with(asOwner(OWNER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(mappings.getHandlerMethods().keySet().stream()
                .map(Object::toString).toList())
                .noneMatch(mapping -> mapping.contains("/api/reminders/internal/")
                        || mapping.contains("/api/reminders/deliver")
                        || mapping.contains("/api/reminder-settings/"));
    }
}
