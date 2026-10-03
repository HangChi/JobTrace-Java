package com.jobtrace.admin.domain;

public record AdminAuditEvent(
        String id, String requestId, String actorId, String actorIdentifier,
        boolean actorDeleted, String targetUserId, String targetIdentifier,
        boolean targetDeleted, String eventType, String outcome, String reason,
        AccessState before, AccessState after, String failureCode, String createdAt) {

    public record AccessState(String role, boolean disabled, long accessVersion) {}
}
