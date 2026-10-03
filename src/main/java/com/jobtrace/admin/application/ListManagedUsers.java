package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.domain.PageResult;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListManagedUsers {

    private final AdminReadQuery query;

    public ListManagedUsers(AdminReadQuery query) {
        this.query = query;
    }

    public PageResult<AdminUser.Summary> execute(AdminQuery.Users criteria) {
        Objects.requireNonNull(criteria);
        PageResult<AdminUser.Summary> result = query.users(criteria);
        if (result.totalPages() > 0 && criteria.page() > result.totalPages()) {
            return query.users(new AdminQuery.Users(criteria.q(), criteria.role(), criteria.status(),
                    criteria.registeredFrom(), criteria.registeredTo(), result.totalPages(),
                    criteria.limit()));
        }
        return result;
    }
}
