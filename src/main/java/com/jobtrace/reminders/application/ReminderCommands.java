package com.jobtrace.reminders.application;

import com.jobtrace.reminders.domain.Reminder;
import com.jobtrace.reminders.domain.ReminderMutation.Create;
import com.jobtrace.reminders.domain.ReminderMutation.Update;
import com.jobtrace.reminders.domain.ReminderMutation.Transition;
import com.jobtrace.reminders.domain.ReminderOperationException;
import com.jobtrace.reminders.domain.ReminderOperationException.Kind;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReminderCommands {

    private final ReminderCommandStore store;
    private final ReminderReadQuery reads;
    private final Clock clock;

    public ReminderCommands(ReminderCommandStore store, ReminderReadQuery reads, Clock clock) {
        this.store = store;
        this.reads = reads;
        this.clock = clock;
    }

    public Reminder create(String ownerId, Create command) {
        validateTimes(command.eventAt(), command.notifyAt(), false);
        requireEmail(ownerId, command.emailEnabled());
        return store.create(ownerId, command).orElseThrow(() -> new ReminderOperationException(
                Kind.NOT_FOUND, "没有找到这条投递。"));
    }

    public Reminder update(String ownerId, UUID id, Update command) {
        validateTimes(command.value().eventAt(), command.value().notifyAt(), false);
        requireEmail(ownerId, command.value().emailEnabled());
        return store.update(ownerId, id, command).orElseThrow(() -> new ReminderOperationException(
                Kind.CONFLICT, "提醒已经发生变化，请刷新后重试。"));
    }

    public Reminder transition(String ownerId, UUID id, Transition command) {
        if ("snooze".equals(command.action())) {
            Reminder current = store.findOwned(ownerId, id).orElseThrow(() ->
                    new ReminderOperationException(Kind.NOT_FOUND, "没有找到这条提醒。"));
            validateTimes(Instant.parse(current.eventAt()), command.notifyAt(), true);
        } else if ("reopen".equals(command.action())) {
            validateTimes(command.eventAt(), command.notifyAt(), false);
        }
        return store.transition(ownerId, id, command).orElseThrow(() ->
                new ReminderOperationException(Kind.CONFLICT, "提醒已经发生变化，请刷新后重试。"));
    }

    private void requireEmail(String ownerId, boolean enabled) {
        if (enabled && !reads.emailAvailability(ownerId).available()) {
            throw new ReminderOperationException(Kind.EMAIL_UNAVAILABLE,
                    "请先绑定并验证接收邮箱。");
        }
    }

    private void validateTimes(Instant eventAt, Instant notifyAt, boolean snooze) {
        if (notifyAt == null || !notifyAt.isAfter(clock.instant())
                || (!snooze && (eventAt == null || notifyAt.isAfter(eventAt)))) {
            throw new ReminderOperationException(Kind.VALIDATION, "请检查提醒时间。");
        }
    }
}
