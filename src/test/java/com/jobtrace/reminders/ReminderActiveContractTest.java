package com.jobtrace.reminders;

import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static org.assertj.core.api.Assertions.assertThat;
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
class ReminderActiveContractTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void activeAndEmptyOverviewsMatchSyntheticLegacyResponses() throws Exception {
        assertEquivalent(fixture().get("active"), response(OWNER));
        assertEquivalent(fixture().get("empty"), response(EMPTY_OWNER));
    }

    @Test
    void exactNotificationTimeIsDue() throws Exception {
        JsonNode active = response(OWNER);
        assertThat(active.get(fixture().get("due-boundary").get("group").asText())
                .get(0).get("notifyAt").asText())
                .isEqualTo(fixture().get("due-boundary").get("notifyAt").asText());
    }

    private JsonNode response(String owner) throws Exception {
        String body = mockMvc.perform(get("/api/reminders").with(asOwner(owner)))
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
