package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.jobmarket.application.SyncRunHistoryQuery;
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
    private SyncRunHistoryQuery history;

    @Test
    void adminCanInspectPersistedRunHistoryWithoutClaimingALease() throws Exception {
        var now = Instant.parse("2026-10-08T00:00:00Z");
        jdbc.update("""
                insert into job_market_sync_runs(source_id,trigger,status,started_at,
                  finished_at,discovered_count,worker_id,request_id)
                values (?,'scheduled','succeeded',?,?,3,'legacy-worker','legacy-request')
                """, SOURCE, java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now.plusSeconds(1)));

        mvc.perform(get("/api/admin/job-market/sync-runs?sourceId=" + SOURCE)
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].trigger").value("scheduled"))
                .andExpect(jsonPath("$.items[0].status").value("succeeded"))
                .andExpect(jsonPath("$.items[0].counts.discovered").value(3));
        mvc.perform(get("/api/admin/job-market/sync-runs").with(asOwner(OWNER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/job-market/sync-runs?limit=101").with(asAdmin()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject(
                "select lease_run_id from job_market_sources where id=?",
                UUID.class, SOURCE)).isNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from job_market_sync_runs", Integer.class)).isEqualTo(1);
    }

    @Test
    void javaRejectsASecondSourceSynchronizationRequest() throws Exception {
        mvc.perform(post("/api/admin/job-market/sources/" + SOURCE + "/sync")
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isGone());
        assertThat(jdbc.queryForObject(
                "select count(*) from job_market_sync_runs", Integer.class)).isZero();
    }

    @Test
    void readOnlyHistoryHandlesEmptyAndInvalidPages() {
        assertThat(history.history(null, 1, 20).total()).isZero();
        assertThat(history.history(SOURCE, 2, 1).items()).isEmpty();
        assertThatThrownBy(() -> history.history(null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.history(null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.history(null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RequestPostProcessor asAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new BridgeIdentity("admin", BridgeIdentity.Role.ADMIN, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
