package com.jobtrace.admin.domain;

import java.util.Arrays;

public enum AdminAccessAction {
    PROMOTE_ADMIN("promote_admin"),
    DEMOTE_ADMIN("demote_admin"),
    DISABLE_USER("disable_user"),
    ENABLE_USER("enable_user");

    private final String value;

    AdminAccessAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AdminAccessAction parse(String value) {
        return Arrays.stream(values())
                .filter(action -> action.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidAdminAccessCommandException(
                        "不支持该账号访问操作。"));
    }
}
