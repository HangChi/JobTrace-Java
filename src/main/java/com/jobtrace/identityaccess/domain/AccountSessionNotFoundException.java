package com.jobtrace.identityaccess.domain;

/** Non-disclosing result for absent, already-revoked, and foreign sessions. */
public final class AccountSessionNotFoundException extends RuntimeException {

    public AccountSessionNotFoundException() {
        super("登录会话已不存在。");
    }
}
