package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminAuditEvent;
import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.domain.PageResult;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListAdminAuditEvents {

    private final AdminReadQuery query;

    public ListAdminAuditEvents(AdminReadQuery query) {
        this.query = query;
    }

    public PageResult<AdminAuditEvent> execute(AdminQuery.Audit criteria) {
        Objects.requireNonNull(criteria);
        PageResult<AdminAuditEvent> result = query.audit(criteria);
        if (result.totalPages() > 0 && criteria.page() > result.totalPages()) {
            return query.audit(new AdminQuery.Audit(criteria.actor(), criteria.target(),
                    criteria.eventType(), criteria.outcome(), criteria.occurredFrom(),
                    criteria.occurredTo(), result.totalPages(), criteria.limit()));
        }
        return result;
    }
}
