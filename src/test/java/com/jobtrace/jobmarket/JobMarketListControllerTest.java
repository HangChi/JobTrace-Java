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
class JobMarketListControllerTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultAndExplicitPagesArePrivateAndOwnerScoped() throws Exception {
        mockMvc.perform(get("/api/job-market/campaigns").with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/job-market/campaigns").with(asOwner(OWNER))
                        .header("x-user-id", OTHER_OWNER))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].company.name").value("Example Labs"))
                .andExpect(jsonPath("$.items[0].isFavorite").value(true));
        mockMvc.perform(get("/api/job-market/campaigns?page=2&limit=1")
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(1));
    }
}
