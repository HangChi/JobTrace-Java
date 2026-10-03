package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class JobMarketDetailContractTest extends JobMarketReadDatabaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void syncedDetailMatchesRepresentativeLegacyFields() throws Exception {
        var actual = detail(CAMPAIGN);
        var fixture = fixtures().get("syncedDetail");

        assertThat(actual.get("id")).isEqualTo(fixture.get("id"));
        assertThat(actual.at("/jobs/0/title")).isEqualTo(fixture.at("/jobs/0/title"));
        assertThat(actual.at("/jobs/0/locations"))
                .isEqualTo(fixture.at("/jobs/0/locations"));
        assertThat(actual.at("/jobs/0/applyUrl"))
                .isEqualTo(fixture.at("/jobs/0/applyUrl"));
        assertThat(actual.at("/jobs/0/sourceName"))
                .isEqualTo(fixture.at("/jobs/0/sourceName"));
        assertThat(actual.at("/jobs/0/alreadyTrackedApplicationId"))
                .isEqualTo(fixture.at("/jobs/0/alreadyTrackedApplicationId"));
        assertThat(actual.get("positions")).hasSize(2);
    }

    @Test
    void directoryStaleUnsafeAndMissingLinksPreserveLegacySemantics() throws Exception {
        jdbc.update("update job_market_company_read_models set "
                + "representative_campaign_id=?::uuid, listing_kind='recruitment_directory', "
                + "recruitment_type='招聘官网', positions=array[]::text[], position_count=0 "
                + "where company_id=?::uuid and include_closed=false", DIRECTORY, COMPANY);
        var directory = detail(DIRECTORY);
        assertThat(directory.get("listingKind"))
                .isEqualTo(fixtures().at("/directoryDetail/listingKind"));
        assertThat(directory.get("jobs")).isEmpty();

        jdbc.update("update job_market_company_read_models set "
                + "representative_campaign_id=?::uuid, listing_kind='synced_jobs' "
                + "where company_id=?::uuid and include_closed=false", CAMPAIGN, COMPANY);
        jdbc.update("update job_market_posts set primary_apply_url='http://unsafe.test' "
                + "where id=?::uuid", JOB);
        var synced = detail(CAMPAIGN);
        assertThat(synced.at("/jobs/0/applyUrl").isNull()).isTrue();
        assertThat(synced.at("/jobs/0/applyUnavailableReason"))
                .isEqualTo(fixtures().at("/unsafeOpenJob/applyUnavailableReason"));
        assertThat(synced.at("/jobs/1/applyUnavailableReason"))
                .isEqualTo(fixtures().at("/staleJob/applyUnavailableReason"));
    }

    private JsonNode detail(String campaignId) throws Exception {
        String body = mockMvc.perform(get("/api/job-market/campaigns/" + campaignId)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode fixtures() throws Exception {
        return mapper.readTree(new ClassPathResource(
                "contracts/jobmarket/read-fixtures.legacy.json")
                .getContentAsString(StandardCharsets.UTF_8));
    }
}
