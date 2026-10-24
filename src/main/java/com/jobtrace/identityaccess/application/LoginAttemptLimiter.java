package com.jobtrace.identityaccess.application;

public interface LoginAttemptLimiter {
    void consumeLoginAttempt();
}
