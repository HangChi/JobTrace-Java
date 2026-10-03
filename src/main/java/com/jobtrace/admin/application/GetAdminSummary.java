package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminSummary;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class GetAdminSummary {

    static final String ACTIVITY_DEFINITION =
            "统计周期内至少创建一次有效登录会话的未禁用用户";

    private final AdminReadQuery query;
    private final Clock clock;

    public GetAdminSummary(AdminReadQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    public AdminSummary execute() {
        AdminSummary.CountsSection counts;
        AdminSummary.ActivitySection activity;
        try {
            counts = AdminSummary.CountsSection.available(query.counts());
        } catch (RuntimeException exception) {
            counts = AdminSummary.CountsSection.unavailable();
        }
        try {
            activity = AdminSummary.ActivitySection.available(query.activity());
        } catch (RuntimeException exception) {
            activity = AdminSummary.ActivitySection.unavailable();
        }
        return new AdminSummary(Instant.now(clock).toString(), "Asia/Shanghai",
                ACTIVITY_DEFINITION, counts, activity);
    }
}
