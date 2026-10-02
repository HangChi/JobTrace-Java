package com.jobtrace.applications;

import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApplicationReadContractTest.FixedClock.class)
class ApplicationReadContractTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void matchesRepresentativePage() throws Exception {
        String response = mockMvc.perform(get("/api/applications").with(asOwner(OWNER)).param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquivalent(fixture("representative-page.legacy.json"), mapper.readTree(response));
    }

    @Test
    void matchesEmptyPage() throws Exception {
        String response = mockMvc.perform(get("/api/applications").with(asOwner("owner-empty")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquivalent(fixture("empty-page.legacy.json"), mapper.readTree(response));
    }

    static com.fasterxml.jackson.databind.JsonNode fixture(String name) throws Exception {
        try (InputStream input = ApplicationReadContractTest.class.getClassLoader()
                .getResourceAsStream("contracts/applications/" + name)) {
            return new ObjectMapper().readTree(input);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock applicationReadClock() {
            return Clock.fixed(Instant.parse("2026-10-02T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
