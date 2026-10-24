package com.jobtrace.admin.domain;

public class AdminAccessConflictException extends RuntimeException {

    private final String code;
    private final String auditEventId;
    private final AdminAccessResult.AccessState latestState;

    public AdminAccessConflictException(
            String code,
            String message,
            String auditEventId,
            AdminAccessResult.AccessState latestState) {
        super(message);
        this.code = code;
        this.auditEventId = auditEventId;
        this.latestState = latestState;
    }

    public String code() {
        return code;
    }

    public String auditEventId() {
        return auditEventId;
    }

    public AdminAccessResult.AccessState latestState() {
        return latestState;
    }
}
