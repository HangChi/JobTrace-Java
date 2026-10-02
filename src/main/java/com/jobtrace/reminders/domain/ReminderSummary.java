package com.jobtrace.reminders.domain;

import com.jobtrace.reminders.domain.ReminderCatalog.Status;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The existing bounded overview shape with stable empty collections. */
public record ReminderSummary(
        List<Reminder> overdue, List<Reminder> upcoming, List<Reminder> history,
        ReminderEmailAvailability email) {

    public ReminderSummary {
        overdue = List.copyOf(Objects.requireNonNull(overdue));
        upcoming = List.copyOf(Objects.requireNonNull(upcoming));
        history = List.copyOf(Objects.requireNonNull(history));
        Objects.requireNonNull(email);
        if (overdue.size() + upcoming.size() + history.size() > 200) {
            throw new IllegalArgumentException("Reminder overview exceeds 200 items");
        }
    }

    public static ReminderSummary group(List<Reminder> ordered, ReminderEmailAvailability email,
            ReminderSelection selection, Instant readInstant) {
        Objects.requireNonNull(ordered);
        Objects.requireNonNull(selection);
        Objects.requireNonNull(readInstant);
        if (ordered.size() > 200) {
            throw new IllegalArgumentException("Reminder overview exceeds 200 items");
        }
        if (selection != ReminderSelection.ACTIVE) {
            Status wanted = selection == ReminderSelection.COMPLETED
                    ? Status.COMPLETED : Status.CANCELLED;
            return new ReminderSummary(List.of(), List.of(), ordered.stream()
                    .filter(item -> item.status() == wanted).toList(), email);
        }
        return new ReminderSummary(
                ordered.stream().filter(item -> item.dueAt(readInstant)).toList(),
                ordered.stream().filter(item -> item.status() == Status.PENDING
                        && !item.dueAt(readInstant)).toList(), List.of(), email);
    }
}
