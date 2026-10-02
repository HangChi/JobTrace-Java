package com.jobtrace.interviews.domain;

import com.jobtrace.interviews.domain.InterviewCatalog.AuthorMode;
import com.jobtrace.interviews.domain.InterviewCatalog.Result;
import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import com.jobtrace.interviews.domain.InterviewCatalog.Visibility;
import java.time.LocalDate;
import java.util.Objects;

public record InterviewSummary(
        String id, String applicationId, String stageOccurrenceId,
        String companyName, String positionName, Stage stage,
        LocalDate interviewedOn, Status status, Result roundResult,
        boolean linked, int questionCount, int actionCount,
        Visibility visibility, AuthorMode authorMode, String publishedAt) {

    public InterviewSummary {
        Objects.requireNonNull(id);
        Objects.requireNonNull(applicationId);
        Objects.requireNonNull(companyName);
        Objects.requireNonNull(positionName);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(interviewedOn);
        Objects.requireNonNull(status);
        Objects.requireNonNull(roundResult);
        Objects.requireNonNull(visibility);
        Objects.requireNonNull(authorMode);
        if (linked != (stageOccurrenceId != null) || questionCount < 0 || actionCount < 0) {
            throw new IllegalArgumentException("Invalid interview summary counts or stage link");
        }
    }
}
