package com.jobtrace.admin.domain;

public record AdminAccessResponse(
        AdminUser.Summary user,
        String auditEventId,
        boolean replayed) {}
