package com.jobtrace.admin.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jobtrace.admin.domain.AdminAccessChange;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AdminAccessRequest(
        String requestId,
        Long expectedVersion,
        String action,
        String reason,
        Boolean confirmSelf) {

    public AdminAccessChange toCommand() {
        return AdminAccessChange.parse(
                requestId, expectedVersion, action, reason, confirmSelf);
    }
}
