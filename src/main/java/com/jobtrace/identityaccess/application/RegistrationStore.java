package com.jobtrace.identityaccess.application;

/** Existing verification-function and Better Auth account persistence boundary. */
public interface RegistrationStore {

    boolean verifyCode(String email, String codeHash);

    void createAccount(String username, String email, String passwordHash, String displayName);
}
