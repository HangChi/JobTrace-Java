package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderCatalog.Status;
import com.jobtrace.reminders.domain.ReminderEmailAvailability;
import com.jobtrace.reminders.domain.ReminderSelection;
import com.jobtrace.reminders.domain.ReminderSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderReadRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Test
    void unknownAndMissingSelectionsUseActive() {
        assertThat(ReminderSelection.from(null)).isEqualTo(ReminderSelection.ACTIVE);
        assertThat(ReminderSelection.from("unexpected")).isEqualTo(ReminderSelection.ACTIVE);
        assertThat(ReminderSelection.from("completed")).isEqualTo(ReminderSelection.COMPLETED);
        assertThat(ReminderSelection.from("cancelled")).isEqualTo(ReminderSelection.CANCELLED);
    }

    @Test
    void pendingAtBoundaryIsDueWithoutStateMutation() {
        Reminder atBoundary = reminder("101", "2026-10-02T00:00:00.000Z", Status.PENDING);
        Reminder future = reminder("102", "2026-10-03T00:00:00.000Z", Status.PENDING);
        Reminder storedDue = reminder("103", "2026-09-30T00:00:00.000Z", Status.DUE);
        ReminderSummary summary = ReminderSummary.group(
                List.of(atBoundary, future, storedDue), new ReminderEmailAvailability(false, null),
                ReminderSelection.ACTIVE, NOW);

        assertThat(summary.overdue()).containsExactly(atBoundary, storedDue);
        assertThat(summary.upcoming()).containsExactly(future);
        assertThat(summary.history()).isEmpty();
        assertThat(atBoundary.status()).isEqualTo(Status.PENDING);
    }

    @Test
    void historyKeepsActiveGroupsEmpty() {
        Reminder completed = reminder("103", "2026-09-30T00:00:00.000Z", Status.COMPLETED);
        ReminderSummary summary = ReminderSummary.group(
                List.of(completed), new ReminderEmailAvailability(false, null),
                ReminderSelection.COMPLETED, NOW);
        assertThat(summary.overdue()).isEmpty();
        assertThat(summary.upcoming()).isEmpty();
        assertThat(summary.history()).containsExactly(completed);
    }

    @Test
    void groupingPreservesSameTimeInputOrderAndEmptyArrays() {
        Reminder first = reminder("101", "2026-10-03T00:00:00.000Z", Status.PENDING);
        Reminder second = reminder("102", "2026-10-03T00:00:00.000Z", Status.PENDING);
        ReminderEmailAvailability email = new ReminderEmailAvailability(false, null);
        ReminderSummary ordered = ReminderSummary.group(
                List.of(first, second), email, ReminderSelection.ACTIVE, NOW);
        ReminderSummary empty = ReminderSummary.group(
                List.of(), email, ReminderSelection.ACTIVE, NOW);

        assertThat(ordered.upcoming()).containsExactly(first, second);
        assertThat(empty.overdue()).isEmpty();
        assertThat(empty.upcoming()).isEmpty();
        assertThat(empty.history()).isEmpty();
    }

    @Test
    void invalidEmailAvailabilityAndOversizedOverviewAreRejected() {
        assertThatThrownBy(() -> new ReminderEmailAvailability(false, "private@example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        List<Reminder> tooMany = java.util.Collections.nCopies(201,
                reminder("101", "2026-10-02T00:00:00.000Z", Status.PENDING));
        assertThatThrownBy(() -> ReminderSummary.group(tooMany,
                new ReminderEmailAvailability(false, null), ReminderSelection.ACTIVE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Reminder reminder(String suffix, String notifyAt, Status status) {
        return new Reminder("00000000-0000-0000-0000-000000000" + suffix,
                "00000000-0000-0000-0000-000000000201", null,
                "Example Labs", "Backend Engineer", "Follow up",
                "2026-10-04T00:00:00.000Z", notifyAt, false, status, 1,
                null, status == Status.COMPLETED ? "2026-10-02T00:00:00.000Z" : null,
                null);
    }
}
