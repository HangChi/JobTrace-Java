package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.ReminderSelection;
import com.jobtrace.reminders.domain.ReminderSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Composes an owner-scoped list and current email availability at one read instant. */
@Service
public class GetReminderSummary {

    private final ReminderReadQuery query;
    private final Clock clock;

    public GetReminderSummary(ReminderReadQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    public ReminderSummary execute(String ownerId, ReminderSelection selection) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        Objects.requireNonNull(selection);
        Instant readInstant = clock.instant();
        return ReminderSummary.group(query.list(ownerId, selection, readInstant),
                query.emailAvailability(ownerId), selection, readInstant);
    }
}
