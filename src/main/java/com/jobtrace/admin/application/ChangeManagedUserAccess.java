package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminAccessChange;
import com.jobtrace.admin.domain.AdminAccessConflictException;
import com.jobtrace.admin.domain.AdminAccessResponse;
import com.jobtrace.admin.domain.AdminAccessResult;
import com.jobtrace.admin.domain.AdminUserNotFoundException;
import com.jobtrace.admin.domain.InvalidAdminUserIdException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChangeManagedUserAccess {

    private static final Map<String, String> FAILURE_MESSAGES = Map.of(
            "access_version_conflict", "账号状态已变化，请刷新后重新确认。",
            "last_admin", "不能禁用或降级最后一个有效管理员。",
            "self_confirmation_required", "当前账号操作需要强化确认。",
            "idempotency_conflict", "请求标识已用于其他操作。",
            "action_state_conflict", "账号当前状态不允许该操作。");

    private final AdminAccessCommand command;
    private final AdminReadQuery query;

    public ChangeManagedUserAccess(AdminAccessCommand command, AdminReadQuery query) {
        this.command = command;
        this.query = query;
    }

    public AdminAccessResponse execute(
            String actorUserId, String targetUserId, AdminAccessChange change) {
        validateTarget(targetUserId);
        AdminAccessResult result = command.execute(actorUserId, targetUserId, change);
        if (result.outcome() != AdminAccessResult.Outcome.SUCCEEDED) {
            String code = result.failureCode() == null
                    ? "admin_change_failed" : result.failureCode();
            throw new AdminAccessConflictException(code,
                    FAILURE_MESSAGES.getOrDefault(code, "操作未完成，请核对最新状态。"),
                    result.auditEventId(), result.latestState());
        }
        var user = query.user(targetUserId)
                .orElseThrow(AdminUserNotFoundException::new);
        return new AdminAccessResponse(user, result.auditEventId(), result.replayed());
    }

    private static void validateTarget(String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank() || targetUserId.length() > 128) {
            throw new InvalidAdminUserIdException();
        }
    }
}
