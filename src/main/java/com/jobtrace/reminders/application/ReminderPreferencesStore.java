package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.ReminderPreferences;
import java.util.Optional;

public interface ReminderPreferencesStore {
    Optional<ReminderPreferences> update(String ownerId, ReminderPreferences preferences);
}
