package com.jobtrace.reminders.application;

import com.jobtrace.reminders.application.ReminderDeliveryStore.Claim;

public interface ReminderMailDelivery {
    void deliver(Claim claim);
}
