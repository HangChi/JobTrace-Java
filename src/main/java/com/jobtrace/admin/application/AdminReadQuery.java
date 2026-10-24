package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminAuditEvent;
import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.domain.AdminSummary;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.domain.PageResult;
import java.util.List;
import java.util.Optional;

public interface AdminReadQuery {

    AdminSummary.Counts counts();

    AdminSummary.Activity activity();

    PageResult<AdminUser.Summary> users(AdminQuery.Users query);

    Optional<AdminUser.Summary> user(String userId);

    List<AdminAuditEvent> recentAudit(String userId, int limit);

    PageResult<AdminUser.Application> applications(String userId, int page);

    PageResult<AdminUser.Interview> interviews(String userId, int page);

    PageResult<AdminAuditEvent> audit(AdminQuery.Audit query);
}
