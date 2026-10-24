package com.jobtrace.applications.domain;

public class ApplicationWriteException extends RuntimeException {

    private final Kind kind;

    public ApplicationWriteException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind { CONFLICT, VALIDATION, POST_UNAVAILABLE, ALREADY_LINKED }
}
