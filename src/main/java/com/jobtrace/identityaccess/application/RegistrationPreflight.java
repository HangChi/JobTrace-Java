package com.jobtrace.identityaccess.application;

import org.springframework.stereotype.Service;

/** Preserves the legacy limit-before-challenge order for public registration. */
@Service
public class RegistrationPreflight {

    private final RegistrationAttemptLimiter limiter;
    private final RegistrationChallengeVerifier challenge;

    public RegistrationPreflight(RegistrationAttemptLimiter limiter,
            RegistrationChallengeVerifier challenge) {
        this.limiter = limiter;
        this.challenge = challenge;
    }

    public void check(String challengeToken) {
        limiter.consumeRegistrationAttempt();
        challenge.verify(challengeToken);
    }
}
