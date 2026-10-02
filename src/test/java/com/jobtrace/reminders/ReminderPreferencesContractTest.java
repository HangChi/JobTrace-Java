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
class ReminderPreferencesContractTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void customAndDefaultSettingsMatchSyntheticLegacyResponses() throws Exception {
        assertEquivalent(fixture().get("custom-preferences"), response(OWNER));
        assertEquivalent(fixture().get("default-preferences"), response(OTHER_OWNER));
    }

    private JsonNode response(String owner) throws Exception {
        String body = mockMvc.perform(get("/api/reminder-settings").with(asOwner(owner)))
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
