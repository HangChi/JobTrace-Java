package com.jobtrace.identityaccess.application;

public interface PasswordResetDelivery {
    void deliver(String email, String resetUrl);
}
