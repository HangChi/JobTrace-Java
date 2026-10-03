package com.jobtrace.admin.domain;

public class CurrentAdminRequiredException extends RuntimeException {

    public CurrentAdminRequiredException() {
        super("需要当前有效的管理员权限。");
    }
}
