package com.jobtrace.reminders.application;

import com.jobtrace.reminders.application.ReminderDeliveryStore.Claim;
import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderOperationException;
import com.jobtrace.reminders.domain.ReminderOperationException.Kind;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeliverDueReminders {

    private final ReminderDeliveryStore store;
    private final ReminderReadQuery reads;
    private final ReminderMailDelivery mail;
    private final int batchSize;
    private final int leaseSeconds;
    private final int maxAttempts;

    public DeliverDueReminders(ReminderDeliveryStore store, ReminderReadQuery reads,
            ReminderMailDelivery mail,
            @Value("${jobtrace.reminders.batch-size:50}") int batchSize,
            @Value("${jobtrace.reminders.lease-seconds:120}") int leaseSeconds,
            @Value("${jobtrace.reminders.max-attempts:3}") int maxAttempts) {
        if (batchSize < 1 || batchSize > 100 || leaseSeconds < 30 || leaseSeconds > 600
                || maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("Invalid reminder delivery configuration");
        }
        this.store = store;
        this.reads = reads;
        this.mail = mail;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
    }

    public boolean retry(String ownerId, UUID id, int version) {
        Reminder reminder = reads.findOwned(ownerId, id).orElseThrow(() ->
                new ReminderOperationException(Kind.NOT_FOUND, "没有找到这条提醒。"));
        if (reminder.emailStatus() == null
                || reminder.emailStatus() != com.jobtrace.reminders.domain.ReminderCatalog
                        .AttemptStatus.FAILED) {
            throw new ReminderOperationException(Kind.CONFLICT, "当前没有可重试的邮件。");
        }
        if (!store.queueRetry(ownerId, id, version)) {
            throw new ReminderOperationException(Kind.CONFLICT,
                    "提醒已经发生变化，请刷新后重试。");
        }
        return true;
    }

    public Result deliver(Integer requestedLimit) {
        int limit = requestedLimit == null ? batchSize : requestedLimit;
        if (limit < 1 || limit > 100) {
            throw new ReminderOperationException(Kind.VALIDATION, "投递批量无效。");
        }
        var claims = store.claimDue(Math.min(limit, batchSize), leaseSeconds, maxAttempts);
        int sent = 0;
        int failed = 0;
        for (Claim claim : claims) {
            boolean delivered;
            try {
                mail.deliver(claim);
                delivered = true;
            } catch (RuntimeException exception) {
                delivered = false;
            }
            if (!store.finalizeClaim(claim, delivered,
                    delivered ? null : "delivery_failed")) {
                throw new IllegalStateException("Reminder claim was not finalized");
            }
            if (delivered) {
                sent++;
            } else {
                failed++;
            }
        }
        return new Result(claims.size(), sent, failed, 0);
    }

    public record Result(int claimed, int sent, int failed, int skipped) {}
}
