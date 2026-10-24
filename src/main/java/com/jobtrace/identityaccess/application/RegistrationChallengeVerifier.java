package com.jobtrace.identityaccess.application;

public interface RegistrationChallengeVerifier {
    void verify(String token);
}
