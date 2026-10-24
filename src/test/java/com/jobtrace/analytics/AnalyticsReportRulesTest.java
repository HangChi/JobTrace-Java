package com.jobtrace.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.analytics.application.AnalyticsReportAggregate;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.Dimension;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.Metrics;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.ReviewMetrics;
import com.jobtrace.analytics.application.AnalyticsReportRules;
import com.jobtrace.analytics.domain.AnalyticsReportRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsReportRulesTest {

    @Test
    @SuppressWarnings("unchecked")
    void comparesMetricsAndBuildsFunnelQualityAndOtherCity() {
        var cities = new ArrayList<Dimension>();
        for (int i = 0; i < 9; i++) {
            cities.add(new Dimension("city-" + i, 1, 0, 0));
        }
        var current = aggregate(new Metrics(5, 2, 1, 1, 1, 5.0, 0, 0),
                new ReviewMetrics(2, 1, 1, 1), cities);
        var previous = aggregate(new Metrics(2, 1, 0, 0, 0, null, 0, 0),
                new ReviewMetrics(1, 0, 0, 0), List.of());
        var range = AnalyticsReportRange.from(Map.of("period", "30d"),
                LocalDate.parse("2026-10-05"));
        var report = AnalyticsReportRules.build(range, current, previous, List.of("上海"));
        var metrics = (Map<String, Object>) report.get("metrics");
        var applications = (Map<String, Object>) metrics.get("applications");
        assertThat(applications.get("value")).isEqualTo(5.0);
        assertThat(applications.get("previous")).isEqualTo(2.0);
        assertThat(applications.get("delta")).isEqualTo(150.0);
        assertThat((List<?>) report.get("cityBreakdown")).hasSize(9);
        assertThat((List<String>) report.get("summary")).anyMatch(value -> value.contains("最大流失"));
        assertThat(report.get("sampleSufficient")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyAndZeroDenominatorNeverProduceNonFiniteDeltas() {
        var empty = aggregate(new Metrics(0, 0, 0, 0, 0, null, 0, 0),
                new ReviewMetrics(0, 0, 0, 0), List.of());
        var range = AnalyticsReportRange.from(Map.of("period", "all"),
                LocalDate.parse("2026-10-05"));
        var report = AnalyticsReportRules.build(range, empty, null, List.of());
        var metrics = (Map<String, Object>) report.get("metrics");
        assertThat(((Map<String, Object>) metrics.get("interviewRate")).get("value"))
                .isEqualTo(0.0);
        assertThat(((Map<String, Object>) metrics.get("applications")).get("delta"))
                .isNull();
        assertThat(report.get("summary")).isEqualTo(List.of());
    }

    private AnalyticsReportAggregate aggregate(Metrics metrics, ReviewMetrics reviews,
            List<Dimension> cities) {
        return new AnalyticsReportAggregate(metrics, reviews, List.of(), List.of(),
                List.of(new Dimension("campus_recruitment", metrics.applications(),
                        metrics.interviewed(), metrics.offers())), cities, List.of());
    }
}
