package com.jobtrace.identityaccess.domain;

public final class AccountEmailCodeStateException extends RuntimeException {

    public AccountEmailCodeStateException() {
        super("无法发送邮箱验证码，请稍后再试。");
    }
}
