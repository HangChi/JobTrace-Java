package com.jobtrace.datatransfer.domain;

public final class ImportInputException extends RuntimeException {

    public enum Kind { VALIDATION, UNSUPPORTED_FORMAT, PAYLOAD_TOO_LARGE }

    private final Kind kind;

    public ImportInputException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
