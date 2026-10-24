package com.jobtrace.identityaccess.domain;

public final class RegistrationEmailConflictException extends RuntimeException {

    public RegistrationEmailConflictException() {
        super("该邮箱已绑定账号，请更换邮箱或直接登录。");
    }
}
