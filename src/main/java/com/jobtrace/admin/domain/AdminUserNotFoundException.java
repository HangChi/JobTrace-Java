package com.jobtrace.admin.domain;

public final class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException() {
        super("没有找到该用户。");
    }
}
