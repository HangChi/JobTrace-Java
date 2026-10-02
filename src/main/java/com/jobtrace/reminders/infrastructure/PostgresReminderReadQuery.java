package com.jobtrace.reminders.infrastructure;

import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderCatalog.AttemptStatus;
import com.jobtrace.reminders.domain.ReminderCatalog.Status;
import com.jobtrace.reminders.domain.ReminderEmailAvailability;
import com.jobtrace.reminders.domain.ReminderPreferences;
import com.jobtrace.reminders.domain.ReminderSelection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized reminder reads against the unchanged legacy schema. */
@Repository
public class PostgresReminderReadQuery implements ReminderReadQuery {

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresReminderReadQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Reminder> list(String ownerId, ReminderSelection selection, Instant readInstant) {
        List<String> statuses = switch (selection) {
            case ACTIVE -> List.of("pending", "due");
            case COMPLETED -> List.of("completed");
            case CANCELLED -> List.of("cancelled");
        };
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("statuses", statuses)
                .addValue("readInstant", Timestamp.from(readInstant));
        return jdbc.query("""
                select r.id, r.application_id, r.source_stage_occurrence_id,
                  a.company_name, a.position_name, r.title, r.event_at, r.notify_at,
                  r.email_enabled, r.status, r.version, r.completed_at, r.cancelled_at,
                  attempt.status as email_status
                from scheduled_reminders r
                join applications a on a.id = r.application_id and a.owner_id = :ownerId
                left join lateral (
                  select n.status from reminder_notification_attempts n
                  where n.reminder_id = r.id and n.owner_id = :ownerId
                    and n.channel = 'email' and n.scheduled_for = r.notify_at
                  order by n.updated_at desc limit 1
                ) attempt on true
                where r.owner_id = :ownerId and r.status in (:statuses)
                order by case when r.status = 'due' or r.notify_at <= :readInstant
                  then 0 else 1 end, r.notify_at asc, r.id asc
                limit 200
                """, parameters, (rs, row) -> reminder(rs));
    }

    @Override
    public ReminderEmailAvailability emailAvailability(String ownerId) {
        var parameters = new MapSqlParameterSource().addValue("ownerId", ownerId);
        var rows = jdbc.query("""
                select recovery_email, recovery_email_verified_at
                from users where id = :ownerId
                """, parameters, (rs, row) -> {
                    String address = rs.getString("recovery_email");
                    boolean verified = rs.getTimestamp("recovery_email_verified_at") != null;
                    boolean available = verified && address != null && !address.isBlank();
                    return new ReminderEmailAvailability(available, available ? address : null);
                });
        return rows.isEmpty() ? new ReminderEmailAvailability(false, null) : rows.getFirst();
    }

    @Override
    public ReminderPreferences preferences(String ownerId) {
        var parameters = new MapSqlParameterSource().addValue("ownerId", ownerId);
        var rows = jdbc.query("""
                select reminder_home_enabled, reminder_home_view, reminder_default_lead,
                  reminder_default_snooze_minutes, reminder_email_default
                from users where id = :ownerId
                """, parameters, (rs, row) -> new ReminderPreferences(
                rs.getBoolean("reminder_home_enabled"), rs.getString("reminder_home_view"),
                rs.getString("reminder_default_lead"),
                rs.getInt("reminder_default_snooze_minutes"),
                rs.getBoolean("reminder_email_default")));
        return rows.isEmpty() ? ReminderPreferences.defaults() : rows.getFirst();
    }

    private static Reminder reminder(ResultSet rs) throws SQLException {
        String attempt = rs.getString("email_status");
        return new Reminder(rs.getString("id"), rs.getString("application_id"),
                rs.getString("source_stage_occurrence_id"), rs.getString("company_name"),
                rs.getString("position_name"), rs.getString("title"),
                timestamp(rs.getTimestamp("event_at")), timestamp(rs.getTimestamp("notify_at")),
                rs.getBoolean("email_enabled"),
                Status.fromWire(rs.getString("status")).orElseThrow(), rs.getInt("version"),
                attempt == null ? null : AttemptStatus.fromWire(attempt).orElseThrow(),
                timestamp(rs.getTimestamp("completed_at")),
                timestamp(rs.getTimestamp("cancelled_at")));
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : UTC_MILLIS.format(value.toInstant());
    }
}
