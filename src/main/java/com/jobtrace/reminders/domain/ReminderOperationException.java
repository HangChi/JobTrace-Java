package com.jobtrace.reminders.domain;

public final class ReminderOperationException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, VALIDATION, EMAIL_UNAVAILABLE }

    private final Kind kind;

    public ReminderOperationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
