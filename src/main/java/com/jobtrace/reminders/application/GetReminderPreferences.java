package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.ReminderPreferences;
import org.springframework.stereotype.Service;

/** Reads reminder defaults for the verified owner without changing the profile. */
@Service
public class GetReminderPreferences {

    private final ReminderReadQuery query;

    public GetReminderPreferences(ReminderReadQuery query) {
        this.query = query;
    }

    public ReminderPreferences execute(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.preferences(ownerId);
    }
}
