package com.jobtrace.identityaccess.application;

public interface RegistrationEmailCodeStore {

    void consumeSourceAttempt();

    void issue(String email, String codeHash);
}
