package com.jobtrace.analytics.application;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsReportAggregate(
        Metrics metrics,
        ReviewMetrics reviews,
        List<Trend> trend,
        List<StageCount> stages,
        List<Dimension> types,
        List<Dimension> cities,
        List<ReviewStage> reviewsByStage) {

    public record Metrics(int applications, int interviewed, int offers,
            int finalInterviews, int pathOffers, Double medianDaysToFirstInterview,
            int offersWithoutInterview, int offersWithoutFinal) {}
    public record ReviewMetrics(int total, int completed, int resolved, int passed) {}
    public record Trend(LocalDate periodStart, int applications, int interviewed, int offers) {}
    public record StageCount(String stage, int count) {}
    public record Dimension(String key, int applications, int interviewed, int offers) {}
    public record ReviewStage(String stage, int total, int pending, int passed, int failed) {}
}
