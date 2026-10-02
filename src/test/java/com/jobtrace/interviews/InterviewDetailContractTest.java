package com.jobtrace.interviews;

import static com.jobtrace.interviews.InterviewReadDatabaseTest.asOwner;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewDetailContractTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void completeEmptyAndUnlinkedDetailsMatchSyntheticLegacyJson() throws Exception {
        assertEquivalent(fixture("representative-detail.legacy.json"), response(R102));
        assertEquivalent(fixture("empty-detail.legacy.json"), response(R103));
        jdbc.update("delete from application_stage_occurrences where id = ?::uuid",
                "00000000-0000-0000-0000-000000000301");
        assertEquivalent(fixture("unlinked-detail.legacy.json"), response(R101));
    }

    private JsonNode response(String id) throws Exception {
        String body = mockMvc.perform(get("/api/interviews/{id}", id).with(asOwner(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode fixture(String name) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "contracts/interviews/" + name)) {
            return mapper.readTree(input);
        }
    }
}
