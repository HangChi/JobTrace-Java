package com.jobtrace.applications;

import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationReadControllerTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthenticationAndIgnoresPublicOwnerHeader() throws Exception {
        mockMvc.perform(get("/api/applications").header("x-user-id", OWNER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/applications").with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/applications").with(asOwner(OWNER))
                        .header("x-user-id", OTHER_OWNER))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void detailHasPrivateNoStoreResponse() throws Exception {
        mockMvc.perform(get("/api/applications/{id}", FIRST_ID).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.id").value(FIRST_ID));
    }
}
