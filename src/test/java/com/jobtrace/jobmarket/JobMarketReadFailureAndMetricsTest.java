package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jobtrace.jobmarket.web.JobMarketReadMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class JobMarketReadFailureAndMetricsTest extends JobMarketReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bothReadsAreNoStoreAndStorageOutagesReturnSafeCompleteProblems() throws Exception {
        for (String path : paths()) {
            mockMvc.perform(get(path).with(asOwner(OWNER)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("cache-control", "private, no-store"));
        }

        jdbc.execute("drop table job_market_campaigns cascade");
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            for (String path : paths()) {
                String body = mockMvc.perform(get(path).with(asOwner(OWNER)))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("storage_unavailable"))
                        .andExpect(jsonPath("$.requestId").isString())
                        .andExpect(jsonPath("$.items").doesNotExist())
                        .andExpect(jsonPath("$.jobs").doesNotExist())
                        .andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain(OWNER, COMPANY,
                        "Example Labs", "relation", "SQL", "select");
            }
            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(OWNER, COMPANY, CAMPAIGN, "Example Labs", "SQL"));
        } finally {
            root.detachAppender(captured);
        }
    }

    @Test
    void metricLabelsAndLatencyBreachesAreBoundedAndPrivateValueFree() {
        var registry = new SimpleMeterRegistry();
        var metrics = new JobMarketReadMetrics(registry);
        for (String operation : new String[] {"list", "detail"}) {
            for (String outcome : new String[] {"success", "denied_identity", "invalid",
                    "not_found", "dependency_failure", "failure"}) {
                metrics.record(operation, outcome, Duration.ofMillis(501));
            }
            assertThat(registry.find("jobtrace.jobmarket.read.budget_breaches")
                    .tag("operation", operation).counter().count()).isEqualTo(6);
        }
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                    .allMatch(key -> Set.of("operation", "outcome").contains(key));
            assertThat(meter.getId().getTags()).extracting(tag -> tag.getValue())
                    .noneMatch(value -> value.contains(OWNER)
                            || value.contains(COMPANY) || value.contains(CAMPAIGN));
        });
        assertThatThrownBy(() -> metrics.record(OWNER, "success", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.record("list", COMPANY, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String[] paths() {
        return new String[] {
            "/api/job-market/campaigns",
            "/api/job-market/campaigns/" + CAMPAIGN
        };
    }
}
