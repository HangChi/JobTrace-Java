package com.jobtrace.datatransfer.domain;

import java.util.List;
import java.util.Objects;

/** Review fields needed for the established Markdown export, without write state. */
public record InterviewExportDocument(
        String id, String companyName, String positionName, String stage,
        Integer durationMinutes, List<Question> questions,
        String highlights, String gaps, int actionCount) {

    public InterviewExportDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(companyName);
        Objects.requireNonNull(positionName);
        Objects.requireNonNull(stage);
        questions = List.copyOf(questions);
    }

    public record Question(String text, String originalAnswer, String followUpNotes,
            String improvedAnswer, Integer selfRating) {
        public Question {
            Objects.requireNonNull(text);
        }
    }
}
