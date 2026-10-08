package com.jobtrace.reminders.infrastructure;

import com.jobtrace.reminders.application.ReminderPreferencesStore;
import com.jobtrace.reminders.domain.ReminderPreferences;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresReminderPreferencesStore implements ReminderPreferencesStore {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresReminderPreferencesStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReminderPreferences> update(String ownerId, ReminderPreferences preferences) {
        var values = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("homeEnabled", preferences.homeEnabled())
                .addValue("homeView", preferences.homeView())
                .addValue("defaultLead", preferences.defaultLead())
                .addValue("snooze", preferences.defaultSnoozeMinutes())
                .addValue("emailDefault", preferences.emailDefault());
        var rows = jdbc.query("""
                update users set reminder_home_enabled=:homeEnabled,
                  reminder_home_view=:homeView,reminder_default_lead=:defaultLead,
                  reminder_default_snooze_minutes=:snooze,
                  reminder_email_default=:emailDefault,updated_at=now()
                where id=:ownerId
                returning reminder_home_enabled,reminder_home_view,reminder_default_lead,
                  reminder_default_snooze_minutes,reminder_email_default
                """, values, (rs, row) -> new ReminderPreferences(
                rs.getBoolean("reminder_home_enabled"), rs.getString("reminder_home_view"),
                rs.getString("reminder_default_lead"),
                rs.getInt("reminder_default_snooze_minutes"),
                rs.getBoolean("reminder_email_default")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
