package com.jobtrace.reminders.domain;

import java.util.Set;

/** Owner-specific reminder display and creation defaults. */
public record ReminderPreferences(
        boolean homeEnabled, String homeView, String defaultLead,
        int defaultSnoozeMinutes, boolean emailDefault) {

    private static final Set<String> HOME_VIEWS = Set.of("scheduled", "suggestions");
    private static final Set<String> LEADS = Set.of("on_time", "30m", "1h", "1d");
    private static final Set<Integer> SNOOZES = Set.of(30, 60, 1440);

    public ReminderPreferences {
        if (!HOME_VIEWS.contains(homeView) || !LEADS.contains(defaultLead)
                || !SNOOZES.contains(defaultSnoozeMinutes)) {
            throw new IllegalArgumentException("Invalid reminder preferences");
        }
    }

    public static ReminderPreferences defaults() {
        return new ReminderPreferences(true, "scheduled", "1h", 30, false);
    }
}
