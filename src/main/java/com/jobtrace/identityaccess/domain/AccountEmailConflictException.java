package com.jobtrace.identityaccess.domain;

/** Target recovery email already belongs to another account. */
public class AccountEmailConflictException extends RuntimeException {

    public AccountEmailConflictException() {
        super("该邮箱已绑定其他账号。");
    }
}
