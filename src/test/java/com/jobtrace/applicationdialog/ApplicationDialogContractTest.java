package com.jobtrace.applicationdialog;

import static com.jobtrace.migration.ContractComparisonTest.assertEquivalent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrace.interviews.InterviewReadDatabaseTest;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApplicationDialogContractTest.FixedClock.class)
class ApplicationDialogContractTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void composedResponseMatchesLegacyAndPreservesFeature004Application() throws Exception {
        JsonNode dialog = response(APPLICATION_ID);
        assertEquivalent(fixture("representative-dialog.legacy.json"), dialog);
        String existing = mockMvc.perform(get("/api/applications/{id}", APPLICATION_ID)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dialog.get("application")).isEqualTo(mapper.readTree(existing));
    }

    @Test
    void ownedApplicationWithoutReviewsHasEmptyArray() throws Exception {
        String id = "00000000-0000-0000-0000-000000000204";
        jdbc.update("""
                insert into applications (id,owner_id,company_name,position_name,
                  applied_date,type,status,latest_date,version,created_at,updated_at) values
                (?::uuid,?,'Empty Co','Analyst','2026-09-22','campus_recruitment',
                  'submitted','2026-09-22',1,'2026-09-22T00:00:00Z','2026-09-22T00:00:00Z')
                """, id, OWNER);
        assertEquivalent(fixture("empty-dialog.legacy.json"), response(id));
    }

    private JsonNode response(String id) throws Exception {
        String body = mockMvc.perform(get("/api/applications/{id}/detail", id)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("cache-control", "private, no-store"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode fixture(String name) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "contracts/interviews/" + name)) {
            return mapper.readTree(input);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock dialogClock() {
            return Clock.fixed(Instant.parse("2026-10-02T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
