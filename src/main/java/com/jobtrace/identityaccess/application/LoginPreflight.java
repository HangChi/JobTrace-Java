package com.jobtrace.identityaccess.application;

import org.springframework.stereotype.Service;

@Service
public class LoginPreflight {
    private final LoginAttemptLimiter limiter;
    private final RegistrationChallengeVerifier challenge;

    public LoginPreflight(LoginAttemptLimiter limiter, RegistrationChallengeVerifier challenge) {
        this.limiter = limiter;
        this.challenge = challenge;
    }

    public void check(String challengeToken) {
        limiter.consumeLoginAttempt();
        challenge.verify(challengeToken);
    }
}
