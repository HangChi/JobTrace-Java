package com.jobtrace.identityaccess.domain;

/** Established email-change rate-limit response. */
public class AccountEmailRateLimitedException extends RuntimeException {

    public AccountEmailRateLimitedException() {
        super("尝试次数过多，请稍后再试。");
    }
}
