package com.jobtrace.reminders;

import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReminderReadDatabaseTest.FixedClockConfiguration.class)
class ReminderHistoryContractTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void completedCancelledAndUnknownSelectionMatchLegacy() throws Exception {
        assertEquivalent(fixture().get("completed"), response("completed"));
        assertEquivalent(fixture().get("cancelled"), response("cancelled"));
        assertEquivalent(fixture().get("active"), response("unknown"));
    }

    @Test
    void emptyHistoryKeepsAllArraysPresent() throws Exception {
        String body = mockMvc.perform(get("/api/reminders").with(asOwner(EMPTY_OWNER))
                        .param("status", "completed"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquivalent(fixture().get("empty"), mapper.readTree(body));
    }

    private JsonNode response(String statusValue) throws Exception {
        String body = mockMvc.perform(get("/api/reminders").with(asOwner(OWNER))
                        .param("status", statusValue))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode fixture() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "contracts/reminders/read-fixtures.legacy.json")) {
            return mapper.readTree(stream);
        }
    }
}
