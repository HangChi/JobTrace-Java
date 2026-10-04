package com.jobtrace.identityaccess.domain;

/** A username or verified-recovery-email uniqueness conflict. */
public final class RegistrationConflictException extends RuntimeException {

    private final String field;

    public RegistrationConflictException(String field) {
        super("email".equals(field) ? "该邮箱已被使用。" : "该用户名已注册。");
        this.field = field;
    }

    public String field() {
        return field;
    }

    public String fieldMessage() {
        return "email".equals(field) ? "该邮箱已关联其他账号。" : "该用户名已注册，请更换一个。";
    }
}
