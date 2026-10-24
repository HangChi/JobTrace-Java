package com.jobtrace.identityaccess.application;

public interface LoginStore {
    Credential findCredential(String identifier, boolean email);
    String createSession(String userId, String userAgent);

    record Credential(String userId, String passwordHash, String role, boolean disabled) {}
}
