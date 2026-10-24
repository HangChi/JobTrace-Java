package com.jobtrace.identityaccess.domain;

/** Safe failure for unavailable owner/session/credential state. */
public class AccountEmailBindingStateException extends RuntimeException {

    public AccountEmailBindingStateException() {
        super("暂时无法绑定邮箱，请稍后重试。");
    }

    public AccountEmailBindingStateException(Throwable cause) {
        super("暂时无法绑定邮箱，请稍后重试。", cause);
    }
}
