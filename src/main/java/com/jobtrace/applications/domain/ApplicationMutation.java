package com.jobtrace.applications.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApplicationMutation(
        String companyName,
        String positionName,
        LocalDate appliedDate,
        int version,
        LocalDate changeDate,
        UUID jobMarketPostId,
        List<Stage> stages,
        Map<String, Object> payload) {

    public record Stage(ApplicationCatalog.RecruitmentStage stage, LocalDate occurredOn) {}
}
