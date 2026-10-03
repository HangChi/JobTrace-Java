package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jobtrace.identityaccess.web.AccountSessionReadController;
import com.jobtrace.identityaccess.web.AccountSessionReadMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSessionFailureAndMetricsTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void storageFailureReturnsSafeUncacheableProblemWithoutPrivateLogs() throws Exception {
        jdbc.execute("drop table sessions");
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            String body = mockMvc.perform(get(AccountSessionReadController.PATH)
                            .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT)
                            .with(asOwner(OWNER)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("storage_unavailable"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(OWNER, CURRENT, "relation", "SQL", "select");
            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(OWNER, CURRENT, "Secret agent", "SQL"));
        } finally {
            root.detachAppender(captured);
        }
    }

    @Test
    void metricLabelsAndLatencyBreachesAreBounded() {
        var registry = new SimpleMeterRegistry();
        var metrics = new AccountSessionReadMetrics(registry);
        for (String outcome : new String[] {
                "success", "denied_identity", "invalid", "dependency_failure", "failure"}) {
            metrics.record(outcome, Duration.ofMillis(501));
        }
        assertThat(registry.find(
                "jobtrace.identity_access.sessions.read.budget_breaches").counter().count())
                .isEqualTo(5);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                        .allMatch(key -> Set.of("outcome").contains(key)));
        assertThatThrownBy(() -> metrics.record(OWNER, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
