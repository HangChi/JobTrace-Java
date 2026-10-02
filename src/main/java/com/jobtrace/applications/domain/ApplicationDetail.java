package com.jobtrace.applications.domain;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ApplicationDetail(
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
        int version,
        String notes,
        List<StageOccurrence> stageOccurrences,
        List<ApplicationEvent> events,
        String createdAt,
        String updatedAt) {

    public ApplicationDetail {
        Objects.requireNonNull(id);
        Objects.requireNonNull(companyName);
        Objects.requireNonNull(positionName);
        Objects.requireNonNull(appliedDate);
        Objects.requireNonNull(type);
        Objects.requireNonNull(status);
        Objects.requireNonNull(latestDate);
        stages = List.copyOf(Objects.requireNonNull(stages));
        stageOccurrences = List.copyOf(Objects.requireNonNull(stageOccurrences));
        events = List.copyOf(Objects.requireNonNull(events));
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (followUpDays < 0 || version < 1) {
            throw new IllegalArgumentException("follow-up days and version must be valid");
        }
    }

    public record StageOccurrence(String id, RecruitmentStage stage, LocalDate occurredOn) {
        public StageOccurrence {
            Objects.requireNonNull(id);
            Objects.requireNonNull(stage);
            Objects.requireNonNull(occurredOn);
        }
    }

    public record ApplicationEvent(
            String id,
            String type,
            LocalDate occurredOn,
            Object before,
            Object after,
            String createdAt) {
        public ApplicationEvent {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            Objects.requireNonNull(occurredOn);
            Objects.requireNonNull(createdAt);
        }
    }
}
