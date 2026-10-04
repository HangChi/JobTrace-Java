package com.jobtrace.admin.domain;

public final class InvalidAdminUserIdException extends RuntimeException {

    public InvalidAdminUserIdException() {
        super("用户标识无效。");
    }
}
