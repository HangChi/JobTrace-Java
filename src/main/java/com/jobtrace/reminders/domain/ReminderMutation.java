package com.jobtrace.reminders.domain;

import java.time.Instant;
import java.util.UUID;

public final class ReminderMutation {

    private ReminderMutation() {}

    public record Create(UUID applicationId, UUID sourceStageOccurrenceId,
            String title, Instant eventAt, Instant notifyAt, boolean emailEnabled) {}

    public record Update(Create value, int version) {}

    public record Transition(int version, String action, Instant eventAt, Instant notifyAt) {}
}
