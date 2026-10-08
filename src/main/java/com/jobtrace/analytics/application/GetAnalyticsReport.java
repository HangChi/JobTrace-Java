package com.jobtrace.analytics.application;

import com.jobtrace.analytics.domain.AnalyticsReportRange;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import static com.jobtrace.shared.config.TimeConfiguration.BUSINESS_ZONE;

@Service
public class GetAnalyticsReport {

    private final AnalyticsReportQuery query;
    private final Clock clock;

    public GetAnalyticsReport(AnalyticsReportQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    public Map<String, Object> execute(String ownerId, Map<String, String> parameters) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        AnalyticsReportRange range = AnalyticsReportRange.from(parameters,
                LocalDate.now(clock.withZone(BUSINESS_ZONE)));
        var current = query.aggregate(ownerId, range, range.from(), range.to());
        var previous = range.comparisonFrom() == null ? null
                : query.aggregate(ownerId, range, range.comparisonFrom(), range.comparisonTo());
        return AnalyticsReportRules.build(range, current, previous, query.availableCities(ownerId));
    }
}
