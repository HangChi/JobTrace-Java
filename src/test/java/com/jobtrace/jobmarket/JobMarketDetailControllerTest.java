package com.jobtrace.jobmarket;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class JobMarketDetailControllerTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPrivateDetailOnlyForTrustedOwners() throws Exception {
        String path = "/api/job-market/campaigns/" + CAMPAIGN;
        mockMvc.perform(get(path).with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.id").value(CAMPAIGN))
                .andExpect(jsonPath("$.jobs[0].title").value("Backend Engineer"))
                .andExpect(jsonPath("$.jobs[0].alreadyTrackedApplicationId").value(APPLICATION));
    }

    @Test
    void malformedMissingAndIneligibleIdentifiersHaveSafeErrors() throws Exception {
        mockMvc.perform(get("/api/job-market/campaigns/not-a-uuid").with(asOwner(OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        mockMvc.perform(get("/api/job-market/campaigns/00000000-0000-4000-8000-000000000099")
                        .with(asOwner(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mockMvc.perform(get("/api/job-market/campaigns/" + CLOSED_CAMPAIGN)
                        .with(asOwner(OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void storageFailureReturnsUnavailableWithoutPartialContent() throws Exception {
        jdbc.execute("drop table job_market_campaigns cascade");
        mockMvc.perform(get("/api/job-market/campaigns/" + CAMPAIGN).with(asOwner(OWNER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("storage_unavailable"))
                .andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.jobs").doesNotExist());
    }
}
