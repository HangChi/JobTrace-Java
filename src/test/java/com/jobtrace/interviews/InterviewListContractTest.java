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
class InterviewListContractTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void emptyAndRepresentativePagesMatchSyntheticLegacyShape() throws Exception {
        assertEquivalent(fixture("empty-page.legacy.json"), response("owner-empty"));
        assertEquivalent(fixture("representative-page.legacy.json"),
                response(OWNER, "publication", "anonymous"));
        assertEquivalent(fixture("filtered-page.legacy.json"),
                response(OWNER, "status", "pending_review"));
    }

    @Test
    void cursorPageMatchesLegacyNavigationPayload() throws Exception {
        String body = mockMvc.perform(get("/api/interviews")
                        .with(asOwner(OWNER)).param("applicationId", APPLICATION_ID)
                        .param("limit", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquivalent(fixture("cursor-page.legacy.json"), mapper.readTree(body));
    }

    private JsonNode response(String owner, String... parameters) throws Exception {
        var request = get("/api/interviews").with(asOwner(owner));
        if (parameters.length == 2) {
            request.param(parameters[0], parameters[1]);
        }
        String body = mockMvc.perform(request)
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
