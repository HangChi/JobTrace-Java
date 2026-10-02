package com.jobtrace.reminders.domain;

import com.jobtrace.reminders.domain.ReminderCatalog.AttemptStatus;
import com.jobtrace.reminders.domain.ReminderCatalog.Status;
import java.time.Instant;
import java.util.Objects;

/** Private reminder projection; optional timestamps and attempt state retain legacy nulls. */
public record Reminder(
        String id, String applicationId, String sourceStageOccurrenceId,
        String companyName, String positionName, String title,
        String eventAt, String notifyAt, boolean emailEnabled,
        Status status, int version, AttemptStatus emailStatus,
        String completedAt, String cancelledAt) {

    public Reminder {
        Objects.requireNonNull(id);
        Objects.requireNonNull(applicationId);
        Objects.requireNonNull(companyName);
        Objects.requireNonNull(positionName);
        Objects.requireNonNull(title);
        Objects.requireNonNull(eventAt);
        Objects.requireNonNull(notifyAt);
        Objects.requireNonNull(status);
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public boolean dueAt(Instant readInstant) {
        return status == Status.DUE || (status == Status.PENDING
                && !Instant.parse(notifyAt).isAfter(readInstant));
    }
}
