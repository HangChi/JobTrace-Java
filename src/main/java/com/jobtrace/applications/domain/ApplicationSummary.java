package com.jobtrace.applications.domain;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ApplicationSummary(
        String id,
        String companyName,
        String positionName,
        String city,
        String jobUrl,
        LocalDate appliedDate,
        ApplicationType type,
        ApplicationStatus status,
        LocalDate latestDate,
        List<RecruitmentStage> stages,
        boolean needsFollowUp,
        int followUpDays,
        String followUpReason,
        int version) {

    public ApplicationSummary {
        Objects.requireNonNull(id);
        Objects.requireNonNull(companyName);
        Objects.requireNonNull(positionName);
        Objects.requireNonNull(appliedDate);
        Objects.requireNonNull(type);
        Objects.requireNonNull(status);
        Objects.requireNonNull(latestDate);
        stages = List.copyOf(Objects.requireNonNull(stages));
        if (followUpDays < 0 || version < 1) {
            throw new IllegalArgumentException("follow-up days and version must be valid");
        }
    }
}
