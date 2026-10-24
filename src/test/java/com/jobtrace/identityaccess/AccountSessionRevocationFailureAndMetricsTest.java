package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jobtrace.identityaccess.web.AccountSessionCommandMetrics;
import com.jobtrace.identityaccess.web.AccountSessionReadController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSessionRevocationFailureAndMetricsTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void storageFailureReturnsSafeUncacheableProblemWithoutPrivateDiagnostics()
            throws Exception {
        jdbc.execute("drop table sessions");
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            String body = mockMvc.perform(delete(AccountSessionReadController.PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"session-older\"}")
                            .with(asOwner(OWNER)).with(csrf()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("storage_unavailable"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(OWNER, "session-older", "relation", "SQL", "delete");
            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(OWNER, "session-older", "Secret agent", "SQL"));
        } finally {
            root.detachAppender(captured);
        }
    }

    @Test
    void metricLabelsAndOneSecondBudgetAreBounded() {
        var registry = new SimpleMeterRegistry();
        var metrics = new AccountSessionCommandMetrics(registry);
        for (String outcome : new String[] {
                "success", "denied_identity", "invalid", "not_found",
                "dependency_failure", "failure"}) {
            metrics.record(outcome, Duration.ofMillis(1_001));
        }
        assertThat(registry.find(
                "jobtrace.identity_access.sessions.revoke.budget_breaches").counter().count())
                .isEqualTo(6);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                        .allMatch(key -> Set.of("outcome").contains(key)));
        assertThatThrownBy(() -> metrics.record(OWNER, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
