package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ReminderPreferencesWriteTest extends ReminderOperationsDatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void updatesOnlyOwnersSettings() throws Exception {
        mvc.perform(patch("/api/reminder-settings").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"homeEnabled":true,"homeView":"scheduled","defaultLead":"1d",
                                 "defaultSnoozeMinutes":1440,"emailDefault":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLead").value("1d"));
        mvc.perform(get("/api/reminder-settings").with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultSnoozeMinutes").value(1440));
        assertThat(jdbc.queryForObject("select reminder_default_lead from users where id=?",
                String.class, OTHER_OWNER)).isEqualTo("1h");
    }

    @Test
    void invalidSettingsAndMissingOwnerDoNotWrite() throws Exception {
        for (String body : new String[] {"{}", "{\"homeEnabled\":true}", """
                {"homeEnabled":true,"homeView":"unknown","defaultLead":"1h",
                 "defaultSnoozeMinutes":30,"emailDefault":false}
                """}) {
            mvc.perform(patch("/api/reminder-settings").with(asOwner(OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/reminder-settings").with(asOwner("missing-owner")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"homeEnabled":true,"homeView":"scheduled","defaultLead":"1h",
                                 "defaultSnoozeMinutes":30,"emailDefault":false}
                                """))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select reminder_default_lead from users where id=?",
                String.class, OWNER)).isEqualTo("30m");
    }

    @Test
    void rejectsEachInvalidPreferenceFieldAndHugeSnoozeNumber() throws Exception {
        String valid = """
                {"homeEnabled":true,"homeView":"scheduled","defaultLead":"1h",
                 "defaultSnoozeMinutes":30,"emailDefault":false}
                """;
        for (String body : new String[] {
                "[]", valid.replace("\"homeEnabled\":true", "\"homeEnabled\":1"),
                valid.replace("\"homeView\":\"scheduled\"", "\"homeView\":1"),
                valid.replace("\"defaultLead\":\"1h\"", "\"defaultLead\":false"),
                valid.replace("\"defaultLead\":\"1h\"", "\"defaultLead\":\"bad\""),
                valid.replace("\"defaultSnoozeMinutes\":30", "\"defaultSnoozeMinutes\":45"),
                valid.replace("\"defaultSnoozeMinutes\":30",
                        "\"defaultSnoozeMinutes\":18446744073709551646"),
                valid.replace("\"emailDefault\":false", "\"emailDefault\":0")}) {
            mvc.perform(patch("/api/reminder-settings").with(asOwner(OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
