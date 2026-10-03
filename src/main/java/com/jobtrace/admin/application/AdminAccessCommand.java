package com.jobtrace.admin.application;

import com.jobtrace.admin.domain.AdminAccessChange;
import com.jobtrace.admin.domain.AdminAccessResult;

public interface AdminAccessCommand {

    AdminAccessResult execute(String actorUserId, String targetUserId, AdminAccessChange change);
}
