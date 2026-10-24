package com.jobtrace.identityaccess.domain;

public final class RegistrationCodeOriginException extends RuntimeException {

    public RegistrationCodeOriginException() {
        super("请求来源无效。");
    }
}
