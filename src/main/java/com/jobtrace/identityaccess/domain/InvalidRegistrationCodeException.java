package com.jobtrace.identityaccess.domain;

public final class InvalidRegistrationCodeException extends RuntimeException {

    public InvalidRegistrationCodeException() {
        super("邮箱验证码无效或已过期。");
    }
}
