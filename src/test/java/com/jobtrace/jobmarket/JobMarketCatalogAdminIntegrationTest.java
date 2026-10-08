package com.jobtrace.jobmarket;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
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

    @Test
    void listsExistingDirectoryWithPaginationAndSearch() throws Exception {
        mvc.perform(get("/api/admin/job-market/catalog").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1279))
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.items[0].companyName").value("腾讯"));
        mvc.perform(get("/api/admin/job-market/catalog?page=26&limit=50")
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(29));
        mvc.perform(get("/api/admin/job-market/catalog?q=腾讯").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].companyName").value("腾讯"));
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
