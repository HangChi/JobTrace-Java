package com.jobtrace.identityaccess.application;

public interface RegistrationAttemptLimiter {
    void consumeRegistrationAttempt();
}
