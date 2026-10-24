package com.jobtrace.identityaccess.domain;

public final class LoginUnavailableException extends RuntimeException {
    public LoginUnavailableException() {
        super("登录功能暂不可用，请稍后重试。");
    }
}
