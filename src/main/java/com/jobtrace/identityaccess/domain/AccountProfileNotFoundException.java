package com.jobtrace.identityaccess.domain;

public final class AccountProfileNotFoundException extends RuntimeException {

    public AccountProfileNotFoundException() {
        super("没有找到账号资料。");
    }
}
