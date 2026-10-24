package com.jobtrace.interviews.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class InterviewMutation {
    private InterviewMutation() {}

    public record Create(UUID applicationId, UUID stageOccurrenceId, String stage,
            LocalDate interviewedOn, Map<String, Object> payload) {}

    public record Update(int version, Map<String, Object> payload) {}
}
