package com.jobtrace.reminders.domain;

/** The current owner's verified recovery-address availability. */
public record ReminderEmailAvailability(boolean available, String address) {
    public ReminderEmailAvailability {
        if (available != (address != null && !address.isBlank())) {
            throw new IllegalArgumentException("Email availability and address disagree");
        }
    }
}
