package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class JobMarketFilterControllerTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsAllFiltersTogetherAndTreatsTrimmedBlankAsOmitted() throws Exception {
        mockMvc.perform(get("/api/job-market/campaigns")
                        .queryParam("q", " Backend ")
                        .queryParam("company", " Example ")
                        .queryParam("location", " Shanghai ")
                        .queryParam("status", "open")
                        .queryParam("postedFrom", "2026-09-02")
                        .queryParam("favorite", "true")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/job-market/campaigns").queryParam("q", "  ")
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void malformedValuesReturnSafeProblemsWithoutPrivateInputEcho() throws Exception {
        String secret = "private-filter-value";
        var result = mockMvc.perform(get("/api/job-market/campaigns")
                        .queryParam("favorite", secret)
                        .with(asOwner(OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.requestId").isString())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
        for (String invalid : new String[] {"page=0", "limit=101", "status=OPEN",
                "postedFrom=not-a-date"}) {
            mockMvc.perform(get("/api/job-market/campaigns?" + invalid)
                            .with(asOwner(OWNER)))
                    .andExpect(status().isBadRequest());
        }
    }
}
