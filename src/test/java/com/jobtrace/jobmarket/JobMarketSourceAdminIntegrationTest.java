package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class JobMarketSourceAdminIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void administratorCanListRegisterAndUpdateSource() throws Exception {
        mvc.perform(get("/api/admin/job-market/sources").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(post("/api/admin/job-market/sources").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registration()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adapter").value("greenhouse"))
                .andExpect(jsonPath("$.status").value("paused"));
        String id = jdbc.queryForObject("select id::text from job_market_sources "
                + "where external_key = 'new-board'", String.class);
        assertThat(id).isNotNull();

        mvc.perform(patch("/api/admin/job-market/sources/" + id)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\",\"syncIntervalMinutes\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        assertThat(jdbc.queryForObject("select sync_interval_minutes from job_market_sources "
                + "where id = ?::uuid", Integer.class, id)).isEqualTo(120);
    }

    @Test
    void rejectsDuplicateUnsafeAndNonAdminWrites() throws Exception {
        String path = "/api/admin/job-market/sources";
        mvc.perform(post(path).with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registration()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registration()))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration().replace("https://jobs.example.test/new",
                                "https://127.0.0.1/new")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registration()))
                .andExpect(status().isCreated());
        mvc.perform(post(path).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(registration()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from job_market_sources "
                + "where external_key = 'new-board'", Integer.class)).isEqualTo(1);
    }

    @Test
    void missingSourceAndEmptyPatchHaveSafeResponses() throws Exception {
        String path = "/api/admin/job-market/sources/"
                + "00000000-0000-4000-8000-000000000099";
        mvc.perform(patch(path).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"paused\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch(path).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disablingLeasedSourceFailsRunAndClearsLease() throws Exception {
        String id = "20000000-0000-4000-8000-000000000001";
        String runId = "70000000-0000-4000-8000-000000000001";
        jdbc.update("insert into job_market_sync_runs(id,source_id,trigger,status) "
                + "values (?::uuid,?::uuid,'admin','running')", runId, id);
        jdbc.update("update job_market_sources set lease_run_id = ?::uuid, "
                + "leased_by = 'worker', lease_until = now() + interval '5 minutes' "
                + "where id = ?::uuid", runId, id);

        mvc.perform(get("/api/admin/job-market/sources").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].latestRun.id").value(runId));
        mvc.perform(patch("/api/admin/job-market/sources/" + id)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paused\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"))
                .andExpect(jsonPath("$.latestRun.status").value("failed"))
                .andExpect(jsonPath("$.latestRun.errorCode").value("source_disabled"));
        assertThat(jdbc.queryForObject("select lease_run_id from job_market_sources "
                + "where id = ?::uuid", String.class, id)).isNull();
    }

    @Test
    void partialUpdatePreservesOtherFieldsAndUnknownCompanyDoesNotInsert() throws Exception {
        String id = "20000000-0000-4000-8000-000000000001";
        mvc.perform(patch("/api/admin/job-market/sources/" + id)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"syncIntervalMinutes\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        assertThat(jdbc.queryForObject("select sync_interval_minutes from job_market_sources "
                + "where id = ?::uuid", Integer.class, id)).isEqualTo(90);

        String missingCompany = registration().replace(COMPANY,
                "00000000-0000-4000-8000-000000000099");
        mvc.perform(post("/api/admin/job-market/sources").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(missingCompany))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from job_market_sources",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void missingBodiesAndMalformedSourceIdAreValidationErrors() throws Exception {
        mvc.perform(post("/api/admin/job-market/sources").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/job-market/sources/"
                        + "20000000-0000-4000-8000-000000000001")
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/job-market/sources/not-a-uuid")
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paused\"}"))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor asAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new BridgeIdentity("admin", BridgeIdentity.Role.ADMIN, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    private static String registration() {
        return "{\"companyId\":\"" + COMPANY + "\",\"adapter\":\"greenhouse\","
                + "\"externalKey\":\"new-board\",\"baseUrl\":\"https://jobs.example.test/new\","
                + "\"allowedHosts\":[\"jobs.example.test\"],\"countryCodes\":[\"cn\"],"
                + "\"accessBasis\":\"public\",\"isOfficial\":true,"
                + "\"syncIntervalMinutes\":360}";
    }
}
