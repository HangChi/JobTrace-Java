package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderEmailAvailability;
import com.jobtrace.reminders.domain.ReminderSelection;
import com.jobtrace.reminders.domain.ReminderPreferences;
import java.time.Instant;
import java.util.List;

/** Every read is explicitly scoped to a trusted owner. */
public interface ReminderReadQuery {
    List<Reminder> list(String ownerId, ReminderSelection selection, Instant readInstant);
    ReminderEmailAvailability emailAvailability(String ownerId);
    ReminderPreferences preferences(String ownerId);
}
