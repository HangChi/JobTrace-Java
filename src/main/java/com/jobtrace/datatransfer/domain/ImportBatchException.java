package com.jobtrace.datatransfer.domain;

public final class ImportBatchException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT }

    private final Kind kind;

    public ImportBatchException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
