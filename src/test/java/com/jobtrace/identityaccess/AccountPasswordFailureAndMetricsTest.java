package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jobtrace.identityaccess.web.AccountPasswordController;
import com.jobtrace.identityaccess.web.AccountPasswordMetrics;
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
class AccountPasswordFailureAndMetricsTest extends AccountPasswordDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deleteFailureRollsBackCredentialAndReturnsSafeUncacheableProblem()
            throws Exception {
        jdbc.execute("""
                create function reject_password_session_delete() returns trigger
                language plpgsql as $$ begin raise exception 'forced delete failure'; end $$
                """);
        jdbc.execute("""
                create trigger reject_password_session_delete
                before delete on sessions for each row
                execute function reject_password_session_delete()
                """);
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            String body = mockMvc.perform(change("Private-New-123"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("storage_unavailable"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(
                    OWNER, CURRENT_SESSION, CURRENT_PASSWORD, "Private-New-123", CURRENT_HASH,
                    "forced", "SQL", "accounts", "sessions");
            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message).doesNotContain(
                            OWNER, CURRENT_SESSION, CURRENT_PASSWORD,
                            "Private-New-123", CURRENT_HASH));
        } finally {
            root.detachAppender(captured);
        }
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void readDependencyFailureReturnsSafeProblemWithoutMutation() throws Exception {
        jdbc.execute("drop table accounts");
        mockMvc.perform(change("Private-New-123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("storage_unavailable"));
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void metricLabelsAndOneSecondBudgetAreBounded() {
        var registry = new SimpleMeterRegistry();
        var metrics = new AccountPasswordMetrics(registry);
        for (String outcome : new String[] {
                "success", "denied_identity", "invalid", "invalid_password",
                "invalid_state", "dependency_failure", "failure"}) {
            metrics.record(outcome, Duration.ofMillis(1_001));
        }
        assertThat(registry.find(
                "jobtrace.identity_access.password.change.budget_breaches")
                .counter().count()).isEqualTo(7);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey())
                        .allMatch(key -> Set.of("outcome").contains(key)));
        assertThatThrownBy(() -> metrics.record(OWNER, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder change(
            String replacement) {
        return post(AccountPasswordController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT_SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + CURRENT_PASSWORD
                        + "\",\"newPassword\":\"" + replacement + "\"}")
                .with(asOwner(OWNER))
                .with(csrf());
    }
}
