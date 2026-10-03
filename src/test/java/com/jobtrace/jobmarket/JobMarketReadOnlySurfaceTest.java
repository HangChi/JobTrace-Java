package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.jobmarket.web.JobMarketReadController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

@AutoConfigureMockMvc
class JobMarketReadOnlySurfaceTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void onlyListAndDetailGetHandlersExistAndReadsLeaveAllRowsUnchanged() throws Exception {
        var handlers = Arrays.stream(JobMarketReadController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)).toList();
        assertThat(handlers).hasSize(2);
        assertThat(handlers).extracting(method -> method.getName())
                .containsExactlyInAnyOrder("list", "detail");

        long before = totalRows();
        mockMvc.perform(get("/api/job-market/campaigns").with(asOwner(OWNER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/job-market/campaigns/" + CAMPAIGN).with(asOwner(OWNER)))
                .andExpect(status().isOk());
        assertThat(totalRows()).isEqualTo(before);
    }

    @Test
    void mutationMethodsAndOutOfScopeRoutesAreUnavailable() throws Exception {
        String path = "/api/job-market/campaigns";
        mockMvc.perform(post(path).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(path + "/" + CAMPAIGN).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(path + "/" + CAMPAIGN).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        for (String excluded : new String[] {"/favorites", "/sync", "/admin", "/collector"}) {
            mockMvc.perform(get(path + excluded).with(asOwner(OWNER)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void productionContainsNoMigrationRefreshWriteOrFrontendChange() throws Exception {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/jobtrace/jobmarket/infrastructure/"
                        + "PostgresJobMarketReadQuery.java"));
        assertThat(adapter.toLowerCase()).doesNotContain(
                "jdbc.update(", "jdbc.execute(", "insert into ", "delete from ",
                "refresh_job_market", "refresh materialized");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
        assertThat(Files.exists(Path.of("frontend/src/routes/job-market"))).isFalse();
    }

    private long totalRows() {
        return jdbc.queryForObject("""
                select (select count(*) from job_market_companies)
                  + (select count(*) from job_market_campaigns)
                  + (select count(*) from job_market_posts)
                  + (select count(*) from job_market_campaign_favorites)
                  + (select count(*) from application_job_market_links)
                """, Long.class);
    }
}
