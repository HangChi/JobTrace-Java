package com.jobtrace.identityaccess.domain;

/** A browser identity validated against a signed cookie and current session/account rows. */
public record BrowserIdentity(String subject, String sessionId, Role role) {
    public BrowserIdentity {
        if (subject == null || subject.isBlank() || sessionId == null || sessionId.isBlank()
                || role == null) {
            throw new IllegalArgumentException("browser identity must be complete");
        }
    }

    public enum Role {
        USER, ADMIN;

        public static Role fromDatabase(String value) {
            return switch (value) {
                case "user" -> USER;
                case "admin" -> ADMIN;
                default -> throw new IllegalArgumentException("invalid account role");
            };
        }
    }
}
