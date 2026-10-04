package com.jobtrace.identityaccess.domain;

/** Non-disclosing failure for missing or concurrently changed account state. */
public class AccountEmailUnbindingStateException extends RuntimeException {

    public AccountEmailUnbindingStateException() {
        super("暂时无法解绑邮箱，请稍后重试。");
    }

    public AccountEmailUnbindingStateException(Throwable cause) {
        super("暂时无法解绑邮箱，请稍后重试。", cause);
    }
}
