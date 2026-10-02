package com.jobtrace.interviews.domain;

import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import java.time.LocalDate;
import java.util.Objects;

public record StageInterviewSummary(
        String id, Stage stage, LocalDate interviewedOn, Status status,
        int questionCount, String stageOccurrenceId) {
    public StageInterviewSummary {
        Objects.requireNonNull(id);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(interviewedOn);
        Objects.requireNonNull(status);
        if (questionCount < 0) {
            throw new IllegalArgumentException("questionCount must not be negative");
        }
    }
}
