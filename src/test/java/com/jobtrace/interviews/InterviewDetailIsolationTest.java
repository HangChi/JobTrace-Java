package com.jobtrace.interviews;

import static com.jobtrace.interviews.InterviewReadDatabaseTest.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewDetailIsolationTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownedDetailIsPrivateAndMissingMatchesCrossOwnerNotFound() throws Exception {
        mockMvc.perform(get("/api/interviews/{id}", R102).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andExpect(jsonPath("$.questions.length()").value(2));
        String missing = mockMvc.perform(get("/api/interviews/{id}",
                        "00000000-0000-0000-0000-000000000999").with(asOwner(OWNER)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String crossOwner = mockMvc.perform(get("/api/interviews/{id}", R201)
                        .with(asOwner(OWNER)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        var mapper = new ObjectMapper();
        var missingBody = mapper.readTree(missing);
        var crossOwnerBody = mapper.readTree(crossOwner);
        for (String field : new String[] {"status", "title", "detail", "code", "type"}) {
            assertThat(crossOwnerBody.get(field)).as(field).isEqualTo(missingBody.get(field));
        }
    }
}
