package com.jobtrace.identityaccess.domain;

/** Established invalid, expired, exhausted, or replayed code response. */
public class InvalidEmailBindingCodeException extends RuntimeException {

    public InvalidEmailBindingCodeException() {
        super("邮箱验证码无效或已过期。");
    }
}
