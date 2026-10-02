package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationDetailIsolationIntegrationTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingAndOtherOwnerProduceIndistinguishableProblemBodies() throws Exception {
        String missing = mockMvc.perform(get("/api/applications/{id}",
                        "10000000-0000-4000-8000-000000000099").with(asOwner(OWNER))
                        .header("x-request-id", "same-request-id"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String hidden = mockMvc.perform(get("/api/applications/{id}", OTHER_ID).with(asOwner(OWNER))
                        .header("x-request-id", "same-request-id"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        var hiddenProblem = new ObjectMapper().readTree(hidden);
        var missingProblem = new ObjectMapper().readTree(missing);
        for (String field : new String[] {"status", "title", "detail", "type", "code"}) {
            assertThat(hiddenProblem.get(field)).isEqualTo(missingProblem.get(field));
        }
        assertThat(hiddenProblem.toString()).doesNotContain("Private Company");
    }
}
