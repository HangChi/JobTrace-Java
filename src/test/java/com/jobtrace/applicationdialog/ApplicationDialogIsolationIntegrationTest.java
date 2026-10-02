package com.jobtrace.applicationdialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrace.interviews.InterviewReadDatabaseTest;
import com.jobtrace.interviews.application.InterviewReadQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationDialogIsolationIntegrationTest extends InterviewReadDatabaseTest {

    @Autowired
    private InterviewReadQuery query;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void summariesAreOwnerScopedAndOrdered() {
        var summaries = query.listForApplication(OWNER, UUID.fromString(APPLICATION_ID));
        assertThat(summaries).extracting(item -> item.id()).containsExactly(R102, R101);
        assertThat(summaries).extracting(item -> item.questionCount()).containsExactly(2, 1);
        assertThat(query.listForApplication(OWNER, UUID.fromString(OTHER_APPLICATION_ID)))
                .isEmpty();
        jdbc.update("delete from application_stage_occurrences where id = ?::uuid",
                "00000000-0000-0000-0000-000000000301");
        var unlinked = query.listForApplication(OWNER, UUID.fromString(APPLICATION_ID));
        assertThat(unlinked.get(1).stage().value()).isEqualTo("interview_1");
        assertThat(unlinked.get(1).stageOccurrenceId()).isNull();
    }

    @Test
    void missingAndCrossOwnerApplicationsHaveTheSameNotFoundOutcome() throws Exception {
        String missing = mockMvc.perform(get("/api/applications/{id}/detail",
                        "00000000-0000-0000-0000-000000000999").with(asOwner(OWNER)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String other = mockMvc.perform(get("/api/applications/{id}/detail", OTHER_APPLICATION_ID)
                        .with(asOwner(OWNER)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var mapper = new ObjectMapper();
        var missingBody = mapper.readTree(missing);
        var otherBody = mapper.readTree(other);
        for (String field : new String[] {"status", "title", "detail", "code", "type"}) {
            assertThat(otherBody.get(field)).as(field).isEqualTo(missingBody.get(field));
        }
    }
}
