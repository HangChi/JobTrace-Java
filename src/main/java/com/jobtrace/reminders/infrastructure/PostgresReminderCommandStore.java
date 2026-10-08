package com.jobtrace.reminders.infrastructure;

import com.jobtrace.reminders.application.ReminderCommandStore;
import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderMutation.Create;
import com.jobtrace.reminders.domain.ReminderMutation.Update;
import com.jobtrace.reminders.domain.ReminderMutation.Transition;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresReminderCommandStore implements ReminderCommandStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final ReminderReadQuery reads;

    public PostgresReminderCommandStore(NamedParameterJdbcTemplate jdbc, ReminderReadQuery reads) {
        this.jdbc = jdbc;
        this.reads = reads;
    }

    @Override
    @Transactional
    public Optional<Reminder> create(String ownerId, Create command) {
        var values = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("applicationId", command.applicationId())
                .addValue("stageId", command.sourceStageOccurrenceId())
                .addValue("title", command.title())
                .addValue("eventAt", Timestamp.from(command.eventAt()))
                .addValue("notifyAt", Timestamp.from(command.notifyAt()))
                .addValue("emailEnabled", command.emailEnabled());
        List<UUID> created = jdbc.query("""
                insert into scheduled_reminders(owner_id,application_id,
                  source_stage_occurrence_id,title,event_at,notify_at,email_enabled)
                select :ownerId,a.id,cast(:stageId as uuid),:title,:eventAt,:notifyAt,:emailEnabled
                from applications a
                where a.id=:applicationId and a.owner_id=:ownerId
                  and (cast(:stageId as uuid) is null or exists(
                    select 1 from application_stage_occurrences s
                    where s.id=:stageId and s.application_id=a.id))
                returning id
                """, values, (rs, row) -> rs.getObject(1, UUID.class));
        if (created.isEmpty()) {
            return Optional.empty();
        }
        UUID id = created.getFirst();
        if (command.sourceStageOccurrenceId() != null) {
            jdbc.update("""
                    insert into progress_reminder_completions(owner_id,stage_occurrence_id,
                      resolution,reminder_id) values(:ownerId,:stageId,'scheduled',:id)
                    on conflict(owner_id,stage_occurrence_id) do update set
                      resolution='scheduled',reminder_id=excluded.reminder_id,updated_at=now()
                    """, values.addValue("id", id));
        }
        return reads.findOwned(ownerId, id);
    }

    @Override
    @Transactional
    public Optional<Reminder> update(String ownerId, UUID id, Update command) {
        Create value = command.value();
        var values = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("id", id).addValue("version", command.version())
                .addValue("title", value.title())
                .addValue("eventAt", Timestamp.from(value.eventAt()))
                .addValue("notifyAt", Timestamp.from(value.notifyAt()))
                .addValue("emailEnabled", value.emailEnabled());
        List<UUID> updated = jdbc.query("""
                update scheduled_reminders r set title=:title,event_at=:eventAt,
                  notify_at=:notifyAt,email_enabled=:emailEnabled,status='pending',
                  completed_at=null,cancelled_at=null,version=r.version+1,updated_at=now()
                from applications a
                where r.id=:id and r.owner_id=:ownerId and r.application_id=a.id
                  and a.owner_id=:ownerId and r.version=:version
                  and r.status in ('pending','due')
                returning r.id
                """, values, (rs, row) -> rs.getObject(1, UUID.class));
        return updated.isEmpty() ? Optional.empty() : reads.findOwned(ownerId, id);
    }

    @Override
    @Transactional
    public Optional<Reminder> transition(String ownerId, UUID id, Transition command) {
        var values = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("id", id).addValue("version", command.version())
                .addValue("action", command.action())
                .addValue("eventAt", command.eventAt() == null
                        ? null : Timestamp.from(command.eventAt()))
                .addValue("notifyAt", command.notifyAt() == null
                        ? null : Timestamp.from(command.notifyAt()));
        List<UUID> updated = jdbc.query("""
                update scheduled_reminders set
                  status=case when :action='complete' then 'completed'
                    when :action='cancel' then 'cancelled' else 'pending' end,
                  completed_at=case when :action='complete' then now() else null end,
                  cancelled_at=case when :action='cancel' then now() else null end,
                  event_at=coalesce(cast(:eventAt as timestamptz),event_at),
                  notify_at=coalesce(cast(:notifyAt as timestamptz),notify_at),
                  version=version+1,updated_at=now()
                where id=:id and owner_id=:ownerId and version=:version
                  and ((:action in ('complete','cancel','snooze')
                        and status in ('pending','due'))
                    or (:action='reopen' and status in ('completed','cancelled')))
                returning id
                """, values, (rs, row) -> rs.getObject(1, UUID.class));
        return updated.isEmpty() ? Optional.empty() : reads.findOwned(ownerId, id);
    }

    @Override
    public Optional<Reminder> findOwned(String ownerId, UUID id) {
        return reads.findOwned(ownerId, id);
    }
}
