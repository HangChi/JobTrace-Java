package com.jobtrace.interviews.domain;

import com.jobtrace.interviews.domain.InterviewCatalog.AuthorMode;
import com.jobtrace.interviews.domain.InterviewCatalog.Format;
import com.jobtrace.interviews.domain.InterviewCatalog.Result;
import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import com.jobtrace.interviews.domain.InterviewCatalog.Visibility;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record InterviewDetail(
        String id, String applicationId, String stageOccurrenceId,
        String companyName, String positionName, Stage stage,
        LocalDate interviewedOn, Status status, Result roundResult,
        boolean linked, int questionCount, int actionCount,
        Visibility visibility, AuthorMode authorMode, String publishedAt,
        Format format, Integer durationMinutes, String interviewerNotes,
        String highlights, String gaps, int version,
        List<InterviewQuestion> questions, List<InterviewActionItem> actionItems,
        String createdAt, String updatedAt) {

    public InterviewDetail {
        questions = List.copyOf(Objects.requireNonNull(questions));
        actionItems = List.copyOf(Objects.requireNonNull(actionItems));
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        new InterviewSummary(id, applicationId, stageOccurrenceId, companyName,
                positionName, stage, interviewedOn, status, roundResult, linked,
                questionCount, actionCount, visibility, authorMode, publishedAt);
    }
}
