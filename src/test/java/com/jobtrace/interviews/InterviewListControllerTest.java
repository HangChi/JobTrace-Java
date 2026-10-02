package com.jobtrace.interviews;

import static com.jobtrace.interviews.InterviewReadDatabaseTest.asOwner;
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
class InterviewListControllerTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsOrdinaryPrincipalAndIgnoresPublicOwnerHeader() throws Exception {
        mockMvc.perform(get("/api/interviews").with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/interviews").with(asOwner(OWNER))
                        .header("x-user-id", OTHER_OWNER))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.limit").value(50));
    }

    @Test
    void strictInvalidFilterDateLimitAndCursorHaveSafeProblem() throws Exception {
        for (String[] value : new String[][] {
                {"status", "invalid"}, {"interviewedFrom", "bad"},
                {"limit", "101"}, {"cursor", "?"}}) {
            mockMvc.perform(get("/api/interviews").with(asOwner(OWNER))
                            .param(value[0], value[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(jsonPath("$.requestId").exists());
        }
    }
}
