package com.jobtrace.identityaccess.domain;

/** Established password mismatch without credential details. */
public class AccountEmailBindingInvalidPasswordException extends RuntimeException {

    public AccountEmailBindingInvalidPasswordException() {
        super("当前密码不正确。");
    }
}
