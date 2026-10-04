package com.jobtrace.admin.domain;

import java.util.List;
import java.util.Objects;

public final class AdminUser {

    private AdminUser() {}

    public record Summary(
            String id, String username, String email, String internalEmail, String role,
            boolean disabled, long accessVersion, String createdAt, String lastSignInAt,
            int applicationCount, int interviewCount) {}

    public record Stage(String stage, String occurredOn) {}

    public record Application(
            String id, String companyName, String positionName, String city, String jobUrl,
            String type, String status, String appliedDate, String latestDate, String notes,
            List<Stage> stages) {
        public Application {
            stages = List.copyOf(Objects.requireNonNull(stages));
        }
    }

    public record Question(
            String category, String question, String originalAnswer, String followUpNotes,
            String improvedAnswer, Integer selfRating) {}

    public record ActionItem(String content, boolean completed) {}

    public record Interview(
            String id, String applicationId, String companyName, String positionName,
            String stage, String interviewedOn, String status, String roundResult,
            String format, Integer durationMinutes, String interviewerNotes, String highlights,
            String gaps, List<Question> questions, List<ActionItem> actionItems) {
        public Interview {
            questions = List.copyOf(Objects.requireNonNull(questions));
            actionItems = List.copyOf(Objects.requireNonNull(actionItems));
        }
    }

    public record Detail(
            String id, String username, String email, String internalEmail, String role,
            boolean disabled, long accessVersion, String createdAt, String lastSignInAt,
            int applicationCount, int interviewCount, List<AdminAuditEvent> recentAuditEvents,
            PageResult<Application> applications, PageResult<Interview> interviews) {

        public Detail {
            recentAuditEvents = List.copyOf(Objects.requireNonNull(recentAuditEvents));
        }

        public static Detail from(
                Summary user,
                List<AdminAuditEvent> events,
                PageResult<Application> applications,
                PageResult<Interview> interviews) {
            return new Detail(user.id(), user.username(), user.email(), user.internalEmail(),
                    user.role(), user.disabled(), user.accessVersion(), user.createdAt(),
                    user.lastSignInAt(), user.applicationCount(), user.interviewCount(),
                    events, applications, interviews);
        }
    }
}
