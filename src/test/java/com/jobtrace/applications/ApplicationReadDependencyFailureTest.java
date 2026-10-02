package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationReadDependencyFailureTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bothReadsReturnSafeUnavailableProblemWhenStorageFails() throws Exception {
        jdbc.execute("drop table applications cascade");

        for (String path : new String[] {"/api/applications", "/api/applications/" + FIRST_ID}) {
            String body = mockMvc.perform(get(path).with(asOwner(OWNER)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("storage_unavailable"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(OWNER, "relation", "SQL", "select");
        }
    }
}
