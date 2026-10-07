package com.jobtrace.analytics.application;

import com.jobtrace.analytics.domain.AnalyticsReportRange;
import java.time.LocalDate;
import java.util.List;

public interface AnalyticsReportQuery {
    AnalyticsReportAggregate aggregate(String ownerId, AnalyticsReportRange range,
            LocalDate from, LocalDate to);
    List<String> availableCities(String ownerId);
}
