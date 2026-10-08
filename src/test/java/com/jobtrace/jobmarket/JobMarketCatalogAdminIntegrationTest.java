package com.jobtrace.jobmarket;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.jobmarket.application.JobMarketCatalogQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class JobMarketCatalogAdminIntegrationTest extends JobMarketReadDatabaseTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobMarketCatalogQuery catalog;

    @Test
    void listsPersistedCompaniesWithPaginationAndSearch() throws Exception {
        mvc.perform(get("/api/admin/job-market/catalog").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/admin/job-market/catalog?page=1&limit=1")
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/admin/job-market/catalog?q=Example").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].companyName").value("Example Labs"))
                .andExpect(jsonPath("$.items[0].channel").value("official_site"));
        assertThat(jdbc.queryForObject("select count(*) from job_market_sync_runs",
                Integer.class)).isZero();
    }

    @Test
    void reflectsRemoteCatalogChangesWithoutRebuildingStaticAssets() throws Exception {
        jdbc.update("update job_market_companies set canonical_name='Updated Lab' "
                + "where id=?::uuid", COMPANY);
        mvc.perform(get("/api/admin/job-market/catalog?q=Updated").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].companyName").value("Updated Lab"));
        mvc.perform(get("/api/admin/job-market/catalog?q=Example").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void handlesBlankSearchEmptyPagesAndInvalidBounds() {
        assertThat(catalog.list("  ", 1, 1).total()).isEqualTo(2);
        assertThat(catalog.list(null, 3, 1).items()).isEmpty();
        assertThat(catalog.list("Closed", 1, 20).items().getFirst().channel())
                .isEqualTo("existing");
        jdbc.update("""
                insert into job_market_sources(company_id,adapter,external_key,base_url,status)
                values (?::uuid,'greenhouse','closed','https://closed.example.test','active')
                """, CLOSED_COMPANY);
        assertThat(catalog.list("Closed", 1, 20).items().getFirst().channel())
                .isEqualTo("automatic");
        assertThatThrownBy(() -> catalog.list("x".repeat(101), 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.list(null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.list(null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.list(null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOrdinaryIdentityAndInvalidPagination() throws Exception {
        mvc.perform(get("/api/admin/job-market/catalog").with(user("admin")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/job-market/catalog").with(asOwner(OWNER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/job-market/catalog?limit=101").with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor asAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new BridgeIdentity("admin", BridgeIdentity.Role.ADMIN, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
