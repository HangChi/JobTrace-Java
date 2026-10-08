package com.jobtrace.jobmarket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.jobmarket.infrastructure.PostgresSourceSyncStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class JobMarketSyncAdminIntegrationTest extends JobMarketReadDatabaseTest {
    private static final UUID SOURCE = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PostgresSourceSyncStore sync;

    @Test
    void adminCanInspectPagedRunHistoryButOrdinaryOwnerCannot() throws Exception {
        var now = Instant.parse("2026-10-08T00:00:00Z");
        var lease = sync.claimOne(SOURCE, "worker", "request", now).orElseThrow();
        sync.complete(lease, now.plusSeconds(1), true);

        mvc.perform(get("/api/admin/job-market/sync-runs?sourceId=" + SOURCE)
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].trigger").value("admin"))
                .andExpect(jsonPath("$.items[0].status").value("succeeded"))
                .andExpect(jsonPath("$.items[0].counts.discovered").value(0));
        mvc.perform(get("/api/admin/job-market/sync-runs").with(asOwner(OWNER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/job-market/sync-runs?limit=101").with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor asAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new BridgeIdentity("admin", BridgeIdentity.Role.ADMIN, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
