package com.jobtrace.interviews.domain;

public final class InterviewWriteException extends RuntimeException {
    public enum Kind { VALIDATION, CONFLICT }

    private final Kind kind;

    public InterviewWriteException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
