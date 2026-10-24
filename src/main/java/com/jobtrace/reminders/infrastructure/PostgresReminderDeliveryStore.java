package com.jobtrace.reminders.infrastructure;

import com.jobtrace.reminders.application.ReminderDeliveryStore;
import com.jobtrace.reminders.application.ReminderDeliveryStore.Claim;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresReminderDeliveryStore implements ReminderDeliveryStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final int configuredMaxAttempts;

    public PostgresReminderDeliveryStore(NamedParameterJdbcTemplate jdbc,
            @Value("${jobtrace.reminders.max-attempts:3}") int configuredMaxAttempts) {
        this.jdbc = jdbc;
        this.configuredMaxAttempts = configuredMaxAttempts;
    }

    @Override
    @Transactional
    public boolean queueRetry(String ownerId, UUID id, int version) {
        var values = new MapSqlParameterSource().addValue("ownerId", ownerId)
                .addValue("id", id).addValue("version", version)
                .addValue("maxAttempts", configuredMaxAttempts);
        List<UUID> candidates = jdbc.query("""
                select n.id from reminder_notification_attempts n
                join scheduled_reminders r on r.id=n.reminder_id
                where r.id=:id and r.owner_id=:ownerId and r.version=:version
                  and r.status='due' and r.email_enabled
                  and n.scheduled_for=r.notify_at and n.channel='email'
                  and n.status='failed' and n.attempt_count<:maxAttempts
                for update of r,n
                """, values, (rs, row) -> rs.getObject(1, UUID.class));
        if (candidates.isEmpty()) {
            return false;
        }
        jdbc.update("""
                update scheduled_reminders set version=version+1,updated_at=now()
                where id=:id and owner_id=:ownerId
                """, values);
        jdbc.update("""
                update reminder_notification_attempts set status='failed',lease_until=null,
                  updated_at=now()-interval '2 minutes'
                where id=:attemptId
                """, values.addValue("attemptId", candidates.getFirst()));
        return true;
    }

    @Override
    @Transactional
    public List<Claim> claimDue(int limit, int leaseSeconds, int maxAttempts) {
        var values = new MapSqlParameterSource().addValue("limit", limit)
                .addValue("maxAttempts", maxAttempts);
        List<UUID> ids = jdbc.query("""
                select r.id from scheduled_reminders r
                where (r.status='pending' and r.notify_at<=now())
                  or (r.status='due' and r.email_enabled and exists(
                    select 1 from reminder_notification_attempts n
                    where n.reminder_id=r.id and n.scheduled_for=r.notify_at
                      and n.channel='email' and n.attempt_count<:maxAttempts
                      and ((n.status='failed'
                            and n.updated_at<=now()-interval '1 minute')
                        or (n.status='claimed' and n.lease_until<=now()))))
                order by r.notify_at,r.id
                for update of r skip locked
                limit :limit
                """, values, (rs, row) -> rs.getObject(1, UUID.class));
        var claims = new ArrayList<Claim>();
        for (UUID id : ids) {
            var rowValues = new MapSqlParameterSource().addValue("id", id)
                    .addValue("maxAttempts", maxAttempts)
                    .addValue("leaseSeconds", leaseSeconds);
            jdbc.update("""
                    update scheduled_reminders set status='due',updated_at=now()
                    where id=:id and status='pending'
                    """, rowValues);
            List<Candidate> rows = jdbc.query("""
                    select r.id,r.application_id,r.title,r.event_at,r.notify_at,
                      r.email_enabled,a.company_name,a.position_name,
                      case when u.recovery_email_verified_at is not null
                        then u.recovery_email else null end as recipient
                    from scheduled_reminders r
                    join applications a on a.id=r.application_id
                    join users u on u.id=r.owner_id
                    where r.id=:id
                    """, rowValues, (rs, number) -> new Candidate(
                    rs.getObject("id", UUID.class),
                    rs.getObject("application_id", UUID.class),
                    rs.getString("company_name"), rs.getString("position_name"),
                    rs.getString("title"), rs.getTimestamp("event_at"),
                    rs.getTimestamp("notify_at"), rs.getBoolean("email_enabled"),
                    rs.getString("recipient")));
            if (rows.isEmpty()) {
                continue;
            }
            Candidate candidate = rows.getFirst();
            rowValues.addValue("scheduledFor", candidate.notifyAt());
            if (!candidate.emailEnabled() || candidate.recipient() == null) {
                jdbc.update("""
                        insert into reminder_notification_attempts(reminder_id,owner_id,
                          channel,scheduled_for,status,recipient)
                        select r.id,r.owner_id,'email',r.notify_at,'skipped',null
                        from scheduled_reminders r where r.id=:id
                        on conflict(reminder_id,scheduled_for,channel) do update set
                          status=case when reminder_notification_attempts.status='sent'
                            then 'sent' else 'skipped' end,
                          claim_token=null,lease_until=null,recipient=null,updated_at=now()
                        """, rowValues);
                continue;
            }
            UUID token = UUID.randomUUID();
            rowValues.addValue("token", token).addValue("recipient", candidate.recipient());
            List<UUID> claimed = jdbc.query("""
                    insert into reminder_notification_attempts(reminder_id,owner_id,
                      channel,scheduled_for,status,claim_token,lease_until,recipient)
                    select r.id,r.owner_id,'email',r.notify_at,'claimed',:token,
                      now()+(:leaseSeconds * interval '1 second'),:recipient
                    from scheduled_reminders r where r.id=:id
                    on conflict(reminder_id,scheduled_for,channel) do update set
                      status='claimed',claim_token=excluded.claim_token,
                      lease_until=excluded.lease_until,recipient=excluded.recipient,
                      attempt_count=reminder_notification_attempts.attempt_count+1,
                      updated_at=now()
                    where reminder_notification_attempts.attempt_count<:maxAttempts
                      and ((reminder_notification_attempts.status='failed'
                            and reminder_notification_attempts.updated_at
                              <=now()-interval '1 minute')
                        or (reminder_notification_attempts.status='claimed'
                            and reminder_notification_attempts.lease_until<=now()))
                    returning claim_token
                    """, rowValues, (rs, number) -> rs.getObject(1, UUID.class));
            if (!claimed.isEmpty()) {
                claims.add(new Claim(candidate.id(), candidate.applicationId(),
                        candidate.companyName(), candidate.positionName(), candidate.title(),
                        candidate.eventAt().toInstant(), candidate.notifyAt().toInstant(),
                        candidate.recipient(), token));
            }
        }
        return List.copyOf(claims);
    }

    @Override
    public boolean finalizeClaim(Claim claim, boolean sent, String errorCode) {
        var values = new MapSqlParameterSource().addValue("id", claim.id())
                .addValue("scheduledFor", Timestamp.from(claim.notifyAt()))
                .addValue("token", claim.token())
                .addValue("status", sent ? "sent" : "failed")
                .addValue("errorCode", errorCode);
        return jdbc.update("""
                update reminder_notification_attempts set status=:status,
                  error_code=:errorCode,
                  sent_at=case when :status='sent' then now() else null end,
                  lease_until=null,updated_at=now()
                where reminder_id=:id and scheduled_for=:scheduledFor
                  and channel='email' and claim_token=:token and status='claimed'
                """, values) == 1;
    }

    private record Candidate(UUID id, UUID applicationId, String companyName,
            String positionName, String title, Timestamp eventAt, Timestamp notifyAt,
            boolean emailEnabled, String recipient) {}
}
