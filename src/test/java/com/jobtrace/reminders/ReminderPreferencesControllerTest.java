package com.jobtrace.reminders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReminderReadDatabaseTest.FixedClockConfiguration.class)
class ReminderPreferencesControllerTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOnlyTrustedOwnerSettingsWithNoStore() throws Exception {
        mockMvc.perform(get("/api/reminder-settings").with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reminder-settings").with(asOwner(OWNER))
                        .header("x-user-id", OTHER_OWNER))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.homeView").value("suggestions"))
                .andExpect(jsonPath("$.defaultSnoozeMinutes").value(60));
    }
}
