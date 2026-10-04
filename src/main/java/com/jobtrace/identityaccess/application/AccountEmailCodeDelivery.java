package com.jobtrace.identityaccess.application;

public interface AccountEmailCodeDelivery {

    void ensureAvailable();

    void deliver(String email, String code);
}
