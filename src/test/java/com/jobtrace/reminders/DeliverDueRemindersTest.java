package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobtrace.reminders.application.DeliverDueReminders;
import com.jobtrace.reminders.application.ReminderDeliveryStore;
import com.jobtrace.reminders.application.ReminderDeliveryStore.Claim;
import com.jobtrace.reminders.application.ReminderMailDelivery;
import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.ReminderOperationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverDueRemindersTest {

    private final ReminderDeliveryStore store = mock(ReminderDeliveryStore.class);
    private final ReminderReadQuery reads = mock(ReminderReadQuery.class);
    private final ReminderMailDelivery mail = mock(ReminderMailDelivery.class);

    @Test
    void rejectsEveryOutOfBoundsDeliverySetting() {
        for (int[] values : new int[][] {{0, 120, 3}, {101, 120, 3},
                {50, 29, 3}, {50, 601, 3}, {50, 120, 0}, {50, 120, 6}}) {
            assertThatThrownBy(() -> new DeliverDueReminders(store, reads, mail,
                    values[0], values[1], values[2]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsOutOfBoundsRequestLimitBeforeClaiming() {
        var delivery = new DeliverDueReminders(store, reads, mail, 50, 120, 3);
        assertThatThrownBy(() -> delivery.deliver(0))
                .isInstanceOf(ReminderOperationException.class);
        assertThatThrownBy(() -> delivery.deliver(101))
                .isInstanceOf(ReminderOperationException.class);
        verifyNoInteractions(store, mail);
    }

    @Test
    void refusesToReportSentWhenClaimFinalizationFails() {
        var delivery = new DeliverDueReminders(store, reads, mail, 50, 120, 3);
        Claim claim = new Claim(UUID.randomUUID(), UUID.randomUUID(), "Example", "Engineer",
                "Follow up", Instant.parse("2030-10-03T00:00:00Z"),
                Instant.parse("2030-10-02T00:00:00Z"), "owner@example.test", UUID.randomUUID());
        when(store.claimDue(anyInt(), anyInt(), anyInt())).thenReturn(List.of(claim));
        assertThatThrownBy(() -> delivery.deliver(1))
                .isInstanceOf(IllegalStateException.class);
    }
}
