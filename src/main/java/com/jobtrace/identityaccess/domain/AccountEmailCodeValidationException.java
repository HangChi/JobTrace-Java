package com.jobtrace.identityaccess.domain;

public final class AccountEmailCodeValidationException extends RuntimeException {

    private final String field;

    public AccountEmailCodeValidationException(String field) {
        super("请输入有效的邮箱地址。");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
