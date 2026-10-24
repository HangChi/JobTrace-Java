package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.reminders.application.ReminderMailDelivery;
import com.jobtrace.reminders.infrastructure.PostgresReminderDeliveryStore;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "jobtrace.reminders.delivery-secret=0123456789abcdef0123456789abcdef",
        "jobtrace.reminders.batch-size=10"})
@AutoConfigureMockMvc
class ReminderDeliveryIntegrationTest extends ReminderOperationsDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired PostgresReminderDeliveryStore store;
    @MockitoBean ReminderMailDelivery mail;

    @Test
    void retryIsOwnerScopedAndDoesNotSendSynchronously() throws Exception {
        jdbc.update("update scheduled_reminders set status='due' where id=?::uuid", R101);
        mvc.perform(post("/api/reminders/{id}/retry-email", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
        assertThat(jdbc.queryForObject("select version from scheduled_reminders where id=?::uuid",
                Integer.class, R101)).isEqualTo(2);
        mvc.perform(post("/api/reminders/{id}/retry-email", R101)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/reminders/{id}/retry-email", R201)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/reminders/{id}/retry-email", R102)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isConflict());
        verifyNoMoreInteractions(mail);
    }

    @Test
    void retryDoesNotAdvanceVersionWithoutFailedAttempt() {
        jdbc.update("update scheduled_reminders set status='due' where id=?::uuid", R101);
        jdbc.update("delete from reminder_notification_attempts where reminder_id=?::uuid", R101);
        assertThat(store.queueRetry(OWNER, UUID.fromString(R101), 1)).isFalse();
        assertThat(jdbc.queryForObject("select version from scheduled_reminders where id=?::uuid",
                Integer.class, R101)).isEqualTo(1);
    }

    @Test
    void internalSecretClaimsAndFinalizesOnlyOnce() throws Exception {
        mvc.perform(post("/api/internal/reminders/deliver").with(csrf())
                        .header("authorization", "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select status from scheduled_reminders where id=?::uuid",
                String.class, R101)).isEqualTo("pending");
        mvc.perform(post("/api/internal/reminders/deliver")
                        .header("authorization", "Bearer 0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.sent").value(1));
        assertThat(jdbc.queryForObject("""
                select status from reminder_notification_attempts
                where reminder_id=?::uuid and scheduled_for=(
                  select notify_at from scheduled_reminders where id=?::uuid)
                """, String.class, R101, R101)).isEqualTo("sent");
        mvc.perform(post("/api/internal/reminders/deliver")
                        .header("authorization", "Bearer 0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(0));
        verify(mail).deliver(any());
        verifyNoMoreInteractions(mail);
    }

    @Test
    void failedMailLeavesRetriableAttemptAndNoImmediateReclaim() throws Exception {
        doThrow(new IllegalStateException("fake mail failure")).when(mail).deliver(any());
        mvc.perform(post("/api/internal/reminders/deliver")
                        .header("authorization", "Bearer 0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1));
        mvc.perform(post("/api/internal/reminders/deliver")
                        .header("authorization", "Bearer 0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(0));
    }

    @Test
    void rejectsMalformedAuthorizationAndBatchBeforeClaiming() throws Exception {
        for (String authorization : new String[] {"Basic 0123456789abcdef0123456789abcdef",
                "Bearer short", "Bearer 0123456789abcdef0123456789abcdeg"}) {
            mvc.perform(post("/api/internal/reminders/deliver")
                            .header("authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        for (String body : new String[] {"[]", "{\"limit\":0}",
                "{\"limit\":101}", "{\"limit\":\"1\"}",
                "{\"limit\":18446744073709551617}"}) {
            mvc.perform(post("/api/internal/reminders/deliver")
                            .header("authorization", "Bearer 0123456789abcdef0123456789abcdef")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select status from scheduled_reminders where id=?::uuid",
                String.class, R101)).isEqualTo("pending");
        verifyNoMoreInteractions(mail);
    }

    @Test
    void competingWorkersRespectClaimLease() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> store.claimDue(1, 120, 3));
            var second = workers.submit(() -> store.claimDue(1, 120, 3));
            int claimed = first.get(10, TimeUnit.SECONDS).size()
                    + second.get(10, TimeUnit.SECONDS).size();
            assertThat(claimed).isEqualTo(1);
        }
        assertThat(store.claimDue(1, 120, 3)).isEmpty();
        assertThat(jdbc.queryForObject("""
                select status from reminder_notification_attempts
                where reminder_id=?::uuid and scheduled_for=(
                  select notify_at from scheduled_reminders where id=?::uuid)
                """, String.class, R101, R101)).isEqualTo("claimed");
    }

    @Test
    void revokedEmailTurnsFailedAttemptIntoSkippedWithoutSending() throws Exception {
        jdbc.update("update users set recovery_email_verified_at=null where id=?", OWNER);
        mvc.perform(post("/api/internal/reminders/deliver")
                        .header("authorization", "Bearer 0123456789abcdef0123456789abcdef")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(0));
        assertThat(jdbc.queryForObject("""
                select status from reminder_notification_attempts
                where reminder_id=?::uuid and scheduled_for=(
                  select notify_at from scheduled_reminders where id=?::uuid)
                """, String.class, R101, R101)).isEqualTo("skipped");
        verifyNoMoreInteractions(mail);
    }
}
