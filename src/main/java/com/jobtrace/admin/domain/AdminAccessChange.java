package com.jobtrace.admin.domain;

import java.util.Objects;
import java.util.UUID;

public record AdminAccessChange(
        UUID requestId,
        long expectedVersion,
        AdminAccessAction action,
        String reason,
        boolean confirmSelf) {

    public AdminAccessChange {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(action, "action");
        reason = reason == null ? null : reason.trim();
        if (expectedVersion < 1) {
            throw new InvalidAdminAccessCommandException("账号版本必须是正整数。");
        }
        if (reason == null || reason.length() < 10 || reason.length() > 500) {
            throw new InvalidAdminAccessCommandException("操作原因长度必须在 10 到 500 个字符之间。");
        }
    }

    public static AdminAccessChange parse(
            String requestId,
            Long expectedVersion,
            String action,
            String reason,
            Boolean confirmSelf) {
        UUID parsedRequestId;
        try {
            parsedRequestId = UUID.fromString(requestId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidAdminAccessCommandException("请求标识必须是有效的 UUID。");
        }
        return new AdminAccessChange(
                parsedRequestId,
                expectedVersion == null ? 0 : expectedVersion,
                AdminAccessAction.parse(action),
                reason,
                Boolean.TRUE.equals(confirmSelf));
    }
}
