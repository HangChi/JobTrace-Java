package com.jobtrace.identityaccess.domain;

public final class LoginValidationException extends RuntimeException {
    private final String field;

    public LoginValidationException(String field) {
        super("请输入有效的登录信息。");
        this.field = field;
    }

    public String field() { return field; }
}
