package com.jobtrace.identityaccess.domain;

/** Stable response returned after credential replacement and session cleanup commit. */
public record AccountPasswordChangeResult(String message) {

    public static final String SUCCESS_MESSAGE = "密码已更新，其他设备已退出登录。";

    public AccountPasswordChangeResult() {
        this(SUCCESS_MESSAGE);
    }

    public AccountPasswordChangeResult {
        if (!SUCCESS_MESSAGE.equals(message)) {
            throw new IllegalArgumentException("message must use the established success copy");
        }
    }
}
