package com.jobtrace.interviews.domain;

import com.jobtrace.interviews.domain.InterviewCatalog.Publication;
import com.jobtrace.interviews.domain.InterviewCatalog.Result;
import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public record InterviewListCriteria(
        UUID applicationId, String query, List<Status> statuses,
        List<Stage> stages, List<Result> results, List<Publication> publications,
        LocalDate interviewedFrom, LocalDate interviewedTo, String cursor, int limit) {

    public InterviewListCriteria {
        statuses = List.copyOf(statuses);
        stages = List.copyOf(stages);
        results = List.copyOf(results);
        publications = List.copyOf(publications);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit is outside 1-100");
        }
    }

    public static InterviewListCriteria from(
            String applicationId, String query, List<String> statuses,
            List<String> stages, List<String> results, List<String> publications,
            String interviewedFrom, String interviewedTo, String cursor, String limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("query is too long");
        }
        return new InterviewListCriteria(
                applicationId == null || applicationId.isEmpty() ? null : UUID.fromString(applicationId),
                normalized.isEmpty() ? null : normalized,
                parse(statuses, Status::fromWire), parse(stages, Stage::fromWire),
                parse(results, Result::fromWire), parse(publications, Publication::fromWire),
                date(interviewedFrom), date(interviewedTo),
                cursor == null || cursor.isEmpty() ? null : cursor,
                limit == null ? 50 : Integer.parseInt(limit));
    }

    private static <T> List<T> parse(List<String> values, Function<String, Optional<T>> parser) {
        return (values == null ? List.<String>of() : values).stream()
                .filter(value -> !value.isEmpty())
                .map(value -> parser.apply(value).orElseThrow(
                        () -> new IllegalArgumentException("Invalid interview filter")))
                .toList();
    }

    private static LocalDate date(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid interview date", exception);
        }
    }
}
