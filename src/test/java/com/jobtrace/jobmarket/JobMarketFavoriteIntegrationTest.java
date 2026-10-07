package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class JobMarketFavoriteIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobMarketReadQuery query;

    @Test
    void favoriteIsIdempotentAndOwnerScoped() throws Exception {
        String path = "/api/job-market/campaigns/" + DIRECTORY + "/favorite";
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put(path).with(asOwner(OTHER_OWNER)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("cache-control", "private, no-store"))
                    .andExpect(jsonPath("$.campaignId").value(DIRECTORY))
                    .andExpect(jsonPath("$.isFavorite").value(true));
        }
        assertThat(jdbc.queryForObject("select count(*) from job_market_campaign_favorites "
                + "where owner_id = ?", Integer.class, OTHER_OWNER)).isEqualTo(1);
        assertThat(query.list(OTHER_OWNER, MarketplaceQuery.defaults()).items()
                .getFirst().isFavorite()).isTrue();
        assertThat(query.list(OWNER, MarketplaceQuery.defaults()).items()
                .getFirst().isFavorite()).isTrue();
    }

    @Test
    void unfavoriteClearsCompanyMarksOnlyForCurrentOwner() throws Exception {
        jdbc.update("insert into job_market_campaign_favorites(owner_id,campaign_id) "
                + "values (?,?::uuid)", OWNER, DIRECTORY);
        String path = "/api/job-market/campaigns/" + DIRECTORY + "/favorite";
        mvc.perform(delete(path).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(false));
        assertThat(jdbc.queryForObject("select count(*) from job_market_campaign_favorites "
                + "where owner_id = ?", Integer.class, OWNER)).isZero();
        assertThat(query.list(OWNER, MarketplaceQuery.defaults()).items()
                .getFirst().isFavorite()).isFalse();
    }

    @Test
    void rejectsUntrustedAndMissingCampaignsWithoutWriting() throws Exception {
        String path = "/api/job-market/campaigns/" + CAMPAIGN + "/favorite";
        mvc.perform(put(path).with(user(OWNER)).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(asOwner(OTHER_OWNER)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/job-market/campaigns/not-uuid/favorite")
                        .with(asOwner(OTHER_OWNER)).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/job-market/campaigns/"
                        + "00000000-0000-4000-8000-000000000099/favorite")
                        .with(asOwner(OTHER_OWNER)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        assertThat(jdbc.queryForObject("select count(*) from job_market_campaign_favorites "
                + "where owner_id = ?", Integer.class, OTHER_OWNER)).isZero();
    }
}
