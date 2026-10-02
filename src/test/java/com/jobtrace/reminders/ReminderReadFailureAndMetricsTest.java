package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jobtrace.reminders.web.ReminderReadMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReminderReadDatabaseTest.FixedClockConfiguration.class)
class ReminderReadFailureAndMetricsTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bothReadsReturnSafeUnavailableProblemsWithoutPrivateLogs() throws Exception {
        jdbc.execute("drop table users cascade");
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            for (String path : new String[] {"/api/reminders", "/api/reminder-settings"}) {
                String body = mockMvc.perform(get(path).with(asOwner(OWNER)))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("storage_unavailable"))
                        .andExpect(jsonPath("$.requestId").exists())
                        .andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain(OWNER, "owner@example.test",
                        "Follow up", "relation", "SQL", "select");
            }
            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(OWNER, "owner@example.test", "Follow up", "SQL"));
        } finally {
            root.detachAppender(captured);
        }
    }

    @Test
    void metricLabelsAndLatencyBreachesAreBounded() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ReminderReadMetrics(registry);
        for (String operation : new String[] {"summary", "preferences"}) {
            for (String outcome : new String[] {
                    "success", "denied_identity", "dependency_failure", "failure"}) {
                metrics.record(operation, outcome, Duration.ofMillis(501));
            }
            assertThat(registry.find("jobtrace.reminders.read.budget_breaches")
                    .tag("operation", operation).counter().count()).isEqualTo(4);
        }
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                        .allMatch(key -> Set.of("operation", "outcome").contains(key)));
        assertThatThrownBy(() -> metrics.record("owner-a", "success", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
