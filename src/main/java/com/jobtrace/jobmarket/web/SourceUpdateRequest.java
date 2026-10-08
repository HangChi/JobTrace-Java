package com.jobtrace.jobmarket.web;

import com.jobtrace.jobmarket.domain.SourceUpdate;

public record SourceUpdateRequest(
        String status, Integer syncIntervalMinutes, String accessBasis) {

    public SourceUpdate toDomain() {
        return new SourceUpdate(status, syncIntervalMinutes, accessBasis);
    }
}
