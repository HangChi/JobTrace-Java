package com.jobtrace.identityaccess.domain;

/** Safe failure for missing, malformed, expired, or concurrently changed credential state. */
public class AccountPasswordStateException extends RuntimeException {

    public AccountPasswordStateException() {
        super("暂时无法修改密码，请稍后重试。");
    }

    public AccountPasswordStateException(Throwable cause) {
        super("暂时无法修改密码，请稍后重试。", cause);
    }
}
