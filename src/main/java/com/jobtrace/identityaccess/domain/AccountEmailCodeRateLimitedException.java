package com.jobtrace.identityaccess.domain;

public final class AccountEmailCodeRateLimitedException extends RuntimeException {

    public AccountEmailCodeRateLimitedException() {
        super("尝试次数过多，请稍后再试。");
    }
}
