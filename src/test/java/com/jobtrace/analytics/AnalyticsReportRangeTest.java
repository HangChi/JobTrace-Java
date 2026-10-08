package com.jobtrace.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.analytics.domain.AnalyticsReportRange;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.analytics.domain.InvalidAnalyticsRangeException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsReportRangeTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-05");

    @Test
    void defaultsAndComparesNinetyDayWindow() {
        var range = AnalyticsReportRange.from(Map.of("period", "unknown", "type", "bad"), TODAY);
        assertThat(range.period()).isEqualTo("90d");
        assertThat(range.from()).isEqualTo(TODAY.minusDays(89));
        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.comparisonTo()).isEqualTo(range.from().minusDays(1));
        assertThat(range.comparisonFrom()).isEqualTo(range.from().minusDays(90));
        assertThat(range.granularity()).isEqualTo("week");
        assertThat(range.type()).isNull();
    }

    @Test
    void resolvesYearToDateAndAllTime() {
        var ytd = AnalyticsReportRange.from(Map.of("period", "ytd"), TODAY);
        assertThat(ytd.from()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(ytd.comparisonFrom()).isEqualTo(LocalDate.parse("2025-01-01"));
        assertThat(ytd.comparisonTo()).isEqualTo(LocalDate.parse("2025-10-05"));
        assertThat(ytd.granularity()).isEqualTo("month");
        var all = AnalyticsReportRange.from(Map.of("period", "all"), TODAY);
        assertThat(all.from()).isNull();
        assertThat(all.to()).isNull();
        assertThat(all.comparisonFrom()).isNull();
    }

    @Test
    void appliesTypeAndExplicitEmptyCityFilter() {
        var range = AnalyticsReportRange.from(Map.of(
                "type", "campus_recruitment", "city", ""), TODAY);
        assertThat(range.type()).isEqualTo(ApplicationType.CAMPUS_RECRUITMENT);
        assertThat(range.hasCityFilter()).isTrue();
        assertThat(range.city()).isEmpty();
        assertThat(AnalyticsReportRange.from(Map.of("city", "__all__"), TODAY)
                .hasCityFilter()).isFalse();
    }

    @Test
    void validatesCustomDatesAndChoosesMonthlyGranularityForLongRange() {
        var range = AnalyticsReportRange.from(Map.of("period", "custom",
                "from", "2026-01-01", "to", "2026-10-01"), TODAY);
        assertThat(range.granularity()).isEqualTo("month");
        for (Map<String, String> invalid : java.util.List.of(
                Map.of("period", "custom", "from", "bad", "to", "2026-09-01"),
                Map.of("period", "custom", "from", "2026-10-02", "to", "2026-10-01"),
                Map.of("period", "custom", "from", "2026-10-01", "to", "2026-10-06"))) {
            assertThatThrownBy(() -> AnalyticsReportRange.from(invalid, TODAY))
                    .isInstanceOf(InvalidAnalyticsRangeException.class);
        }
    }
}
