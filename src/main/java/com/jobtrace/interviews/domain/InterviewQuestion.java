package com.jobtrace.interviews.domain;

import com.jobtrace.interviews.domain.InterviewCatalog.QuestionCategory;
import java.util.Objects;

public record InterviewQuestion(
        String id, QuestionCategory category, String question,
        String originalAnswer, String followUpNotes, String improvedAnswer,
        Integer selfRating) {
    public InterviewQuestion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(category);
        Objects.requireNonNull(question);
        if (selfRating != null && (selfRating < 1 || selfRating > 5)) {
            throw new IllegalArgumentException("selfRating is outside 1-5");
        }
    }
}
