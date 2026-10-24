package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.domain.AdminUserNotFoundException;
import com.jobtrace.admin.domain.InvalidAdminUserIdException;
import com.jobtrace.admin.domain.PageResult;
import org.springframework.stereotype.Service;

@Service
public class GetManagedUserDetail {

    private static final int RECENT_AUDIT_LIMIT = 10;
    private final AdminReadQuery query;

    public GetManagedUserDetail(AdminReadQuery query) {
        this.query = query;
    }

    public AdminUser.Detail execute(String userId, AdminQuery.Content content) {
        if (userId == null || userId.isBlank() || userId.length() > 128) {
            throw new InvalidAdminUserIdException();
        }
        AdminUser.Summary user = query.user(userId).orElseThrow(() ->
                new AdminUserNotFoundException());
        PageResult<AdminUser.Application> applications =
                clampApplications(userId, content.applicationsPage());
        PageResult<AdminUser.Interview> interviews =
                clampInterviews(userId, content.interviewsPage());
        return AdminUser.Detail.from(user, query.recentAudit(userId, RECENT_AUDIT_LIMIT),
                applications, interviews);
    }

    private PageResult<AdminUser.Application> clampApplications(String userId, int page) {
        PageResult<AdminUser.Application> result = query.applications(userId, page);
        return result.totalPages() > 0 && page > result.totalPages()
                ? query.applications(userId, result.totalPages()) : result;
    }

    private PageResult<AdminUser.Interview> clampInterviews(String userId, int page) {
        PageResult<AdminUser.Interview> result = query.interviews(userId, page);
        return result.totalPages() > 0 && page > result.totalPages()
                ? query.interviews(userId, result.totalPages()) : result;
    }
}
