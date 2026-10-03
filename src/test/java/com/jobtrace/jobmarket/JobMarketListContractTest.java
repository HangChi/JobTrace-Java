package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class JobMarketListContractTest extends JobMarketReadDatabaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void representativeListMatchesLegacyFixtureFields() throws Exception {
        String response = mockMvc.perform(get("/api/job-market/campaigns").with(asOwner(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var actual = mapper.readTree(response);
        var fixture = mapper.readTree(new ClassPathResource(
                "contracts/jobmarket/read-fixtures.legacy.json")
                .getContentAsString(StandardCharsets.UTF_8)).get("defaultPage");
        assertThat(actual.get("page")).isEqualTo(fixture.get("page"));
        assertThat(actual.get("limit")).isEqualTo(fixture.get("limit"));
        assertThat(actual.get("total")).isEqualTo(fixture.get("total"));
        assertThat(actual.at("/items/0/company")).isEqualTo(fixture.at("/items/0/company"));
        assertThat(actual.at("/items/0/positions")).isEqualTo(fixture.at("/items/0/positions"));
        assertThat(actual.at("/items/0/publishedAt"))
                .isEqualTo(fixture.at("/items/0/publishedAt"));
    }
}
