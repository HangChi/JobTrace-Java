package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderEmailAvailability;
import com.jobtrace.reminders.domain.ReminderSelection;
import com.jobtrace.reminders.domain.ReminderPreferences;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every read is explicitly scoped to a trusted owner. */
public interface ReminderReadQuery {
    List<Reminder> list(String ownerId, ReminderSelection selection, Instant readInstant);
    ReminderEmailAvailability emailAvailability(String ownerId);
    ReminderPreferences preferences(String ownerId);
    Optional<Reminder> findOwned(String ownerId, UUID id);
}
