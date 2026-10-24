package com.jobtrace.identityaccess.application;

public interface LogoutSessionStore {
    boolean deleteByToken(String verifiedToken);
}
