package com.jobtrace.analytics.application;

import static com.jobtrace.shared.config.TimeConfiguration.BUSINESS_ZONE;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetAnalyticsSummary {

    private final Query query;
    private final Clock clock;

    public GetAnalyticsSummary(Query query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    public AnalyticsSummary execute(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.getSummary(ownerId, LocalDate.now(clock.withZone(BUSINESS_ZONE)));
    }

    public interface Query {
        AnalyticsSummary getSummary(String ownerId, LocalDate today);
    }

    public record AnalyticsSummary(
            int total,
            int submitted,
            int refused,
            int offers,
            int addedThisWeek,
            Map<String, Integer> stageDistribution,
            List<FollowUp> followUps,
            List<ProgressReminder> progressReminders) {

        public AnalyticsSummary {
            stageDistribution = Map.copyOf(Objects.requireNonNull(stageDistribution));
            followUps = List.copyOf(Objects.requireNonNull(followUps));
            progressReminders = List.copyOf(Objects.requireNonNull(progressReminders));
        }
    }

    public record FollowUp(
            String id,
            String companyName,
            String positionName,
            String city,
            String jobUrl,
            LocalDate appliedDate,
            String type,
            String status,
            LocalDate latestDate,
            List<String> stages,
            boolean needsFollowUp,
            int followUpDays,
            String followUpReason,
            int version) {

        public FollowUp {
            stages = List.copyOf(Objects.requireNonNull(stages));
        }
    }

    public record ProgressReminder(
            String id,
            String applicationId,
            String companyName,
            String positionName,
            String city,
            String stageOccurrenceId,
            String stage,
            LocalDate occurredOn,
            String reviewId,
            String reviewStatus,
            boolean completed) {}
}
