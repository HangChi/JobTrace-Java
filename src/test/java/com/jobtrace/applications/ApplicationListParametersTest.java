package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.web.ApplicationListParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationListParametersTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preservesRepeatedValuesAndLegacyDefaults() {
        var values = new LinkedMultiValueMap<String, String>();
        values.add("status", "submitted");
        values.add("status", "unknown");
        values.add("status", "offer");
        values.add("limit", "bad");
        var criteria = ApplicationListParameters.from(values);

        assertThat(criteria.statuses()).containsExactly(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFER);
        assertThat(criteria.limit()).isEqualTo(50);
        assertThat(criteria.defaultOrder()).isTrue();
    }

    @Test
    void malformedCursorIsAValidationProblem() throws Exception {
        mockMvc.perform(get("/api/applications").with(asOwner(OWNER)).param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
    }
}
