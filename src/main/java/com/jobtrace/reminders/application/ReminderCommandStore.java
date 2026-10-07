package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderMutation.Create;
import com.jobtrace.reminders.domain.ReminderMutation.Update;
import com.jobtrace.reminders.domain.ReminderMutation.Transition;
import java.util.Optional;
import java.util.UUID;

public interface ReminderCommandStore {
    Optional<Reminder> create(String ownerId, Create command);
    Optional<Reminder> update(String ownerId, UUID id, Update command);
    Optional<Reminder> transition(String ownerId, UUID id, Transition command);
    Optional<Reminder> findOwned(String ownerId, UUID id);
}
