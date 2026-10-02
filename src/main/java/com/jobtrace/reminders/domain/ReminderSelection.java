package com.jobtrace.reminders.domain;

/** The existing route treats every unknown selection as active. */
public enum ReminderSelection {
    ACTIVE, COMPLETED, CANCELLED;

    public static ReminderSelection from(String value) {
        if ("completed".equals(value)) {
            return COMPLETED;
        }
        if ("cancelled".equals(value)) {
            return CANCELLED;
        }
        return ACTIVE;
    }
}
