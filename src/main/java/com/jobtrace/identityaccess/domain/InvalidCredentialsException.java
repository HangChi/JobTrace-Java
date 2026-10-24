package com.jobtrace.identityaccess.domain;

public final class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("用户名或密码不正确。");
    }
}
