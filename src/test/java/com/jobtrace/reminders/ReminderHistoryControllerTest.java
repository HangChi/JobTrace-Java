package com.jobtrace.reminders;

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
class ReminderHistoryControllerTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void completedAndCancelledSelectionsReturnOnlyHistory() throws Exception {
        for (String selection : new String[] {"completed", "cancelled"}) {
            mockMvc.perform(get("/api/reminders").with(asOwner(OWNER))
                            .param("status", selection))
                    .andExpect(status().isOk())
                    .andExpect(header().string("cache-control", "private, no-store"))
                    .andExpect(jsonPath("$.overdue.length()").value(0))
                    .andExpect(jsonPath("$.upcoming.length()").value(0))
                    .andExpect(jsonPath("$.history.length()").value(1))
                    .andExpect(jsonPath("$.history[0].status").value(selection));
        }
    }
}
