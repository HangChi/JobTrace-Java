package com.jobtrace.admin.domain;

public record AdminAccessResult(
        Outcome outcome,
        String failureCode,
        String auditEventId,
        boolean replayed,
        String userId,
        String role,
        Boolean disabled,
        Long accessVersion) {

    public enum Outcome {
        SUCCEEDED,
        DENIED,
        CONFLICT,
        FAILED;

        public static Outcome parse(String value) {
            try {
                return valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Invalid administration command outcome", exception);
            }
        }
    }

    public AccessState latestState() {
        if (role == null || disabled == null || accessVersion == null) {
            return null;
        }
        return new AccessState(role, disabled, accessVersion);
    }

    public record AccessState(String role, boolean disabled, long accessVersion) {}
}
