package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class JobMarketCandidateIntegrationTest extends JobMarketReadDatabaseTest {
    private static final String SOURCE = "80000000-0000-4000-8000-000000000001";
    private static final String COMPANY_CANDIDATE = "90000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void candidates() {
        jdbc.update("""
                insert into job_market_source_candidates(id,company_id,entry_url,adapter,
                  external_key,base_url,allowed_hosts,confidence,evidence_code,
                  review_status,health_status)
                values (?::uuid,?::uuid,'https://board.example.test/jobs','greenhouse',
                  'board','https://board.example.test/jobs',array['board.example.test'],
                  'high','board_detected','pending','healthy')
                """, SOURCE, COMPANY);
        jdbc.update("""
                insert into job_market_company_candidates(id,company_name,normalized_name,
                  article_url,article_title,source_engine)
                values (?::uuid,'New Company','new company',
                  'https://mp.weixin.qq.com/s/abc','New Company 招聘','sogou')
                """, COMPANY_CANDIDATE);
    }

    @Test
    void sourceCandidateApprovalIsAtomicAndDoesNotDuplicateSources() throws Exception {
        mvc.perform(get("/api/admin/job-market/discovery?status=pending").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SOURCE))
                .andExpect(jsonPath("$.summary.pendingCandidates").value(1));
        mvc.perform(patch("/api/admin/job-market/discovery/" + SOURCE)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("approved"));
        assertThat(jdbc.queryForObject("select count(*) from job_market_sources "
                + "where external_key='board'", Integer.class)).isEqualTo(1);
        mvc.perform(patch("/api/admin/job-market/discovery/" + SOURCE)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from job_market_sources "
                + "where external_key='board'", Integer.class)).isEqualTo(1);
    }

    @Test
    void companyCandidateApprovalCreatesDirectoryOnce() throws Exception {
        mvc.perform(get("/api/admin/job-market/company-candidates").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.pending").value(1));
        mvc.perform(patch("/api/admin/job-market/company-candidates/" + COMPANY_CANDIDATE)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("approved"));
        assertThat(jdbc.queryForObject("select count(*) from job_market_companies "
                + "where normalized_name='new company'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from job_market_campaigns "
                + "where campaign_key='directory:wechat'", Integer.class)).isEqualTo(1);
        mvc.perform(patch("/api/admin/job-market/company-candidates/" + COMPANY_CANDIDATE)
                        .with(asAdmin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ordinaryUsersCannotReviewAndInvalidActionDoesNotWrite() throws Exception {
        String path = "/api/admin/job-market/discovery/" + SOURCE;
        mvc.perform(patch(path).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"publish\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from job_market_sources "
                + "where external_key='board'", Integer.class)).isZero();
    }

    private static RequestPostProcessor asAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new BridgeIdentity("admin", BridgeIdentity.Role.ADMIN, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
