package com.jobtrace.identityaccess.domain;

/** Stable non-disclosing error for a current-password mismatch. */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("当前密码不正确，请重新输入。");
    }
}
