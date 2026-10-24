package com.jobtrace.identityaccess.domain;

/** Required legacy verification secret is unavailable. */
public class AccountEmailBindingUnavailableException extends RuntimeException {

    public AccountEmailBindingUnavailableException() {
        super("邮箱验证暂不可用，请稍后重试。");
    }
}
