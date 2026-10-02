package com.jobtrace.applications;

import static com.jobtrace.applications.ApplicationReadContractTest.fixture;
import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApplicationReadContractTest.FixedClock.class)
class ApplicationDetailContractTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void matchesCompleteLegacyCoreDetail() throws Exception {
        String response = mockMvc.perform(get("/api/applications/{id}", FIRST_ID).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquivalent(fixture("representative-detail.legacy.json"),
                new ObjectMapper().readTree(response));
    }
}
