package com.jobtrace.reminders.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReminderDeliveryStore {
    boolean queueRetry(String ownerId, UUID id, int version);
    List<Claim> claimDue(int limit, int leaseSeconds, int maxAttempts);
    boolean finalizeClaim(Claim claim, boolean sent, String errorCode);

    record Claim(UUID id, UUID applicationId, String companyName, String positionName,
            String title, Instant eventAt, Instant notifyAt, String recipient, UUID token) {}
}
