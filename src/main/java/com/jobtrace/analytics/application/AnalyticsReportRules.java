package com.jobtrace.analytics.application;

import com.jobtrace.analytics.application.AnalyticsReportAggregate.Dimension;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.Metrics;
import com.jobtrace.analytics.application.AnalyticsReportAggregate.ReviewMetrics;
import com.jobtrace.analytics.domain.AnalyticsReportRange;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import java.text.Collator;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure legacy-compatible report calculations; database aggregates are supplied separately. */
public final class AnalyticsReportRules {

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "summer_internship", "暑期实习", "daily_internship", "日常实习",
            "spring_recruitment", "春招", "early_campus_recruitment", "秋招提前批",
            "campus_recruitment", "秋招", "social_recruitment", "社招");
    private static final Collator CHINESE = Collator.getInstance(Locale.CHINA);

    private AnalyticsReportRules() {}

    public static Map<String, Object> build(AnalyticsReportRange range,
            AnalyticsReportAggregate current, AnalyticsReportAggregate previous,
            List<String> availableCities) {
        Metrics m = current.metrics();
        Metrics p = previous == null ? null : previous.metrics();
        ReviewMetrics reviews = current.reviews();
        ReviewMetrics priorReviews = previous == null ? null : previous.reviews();
        Double completion = reviews.total() == 0 ? null : rate(reviews.completed(), reviews.total());
        Double priorCompletion = priorReviews == null || priorReviews.total() == 0
                ? null : rate(priorReviews.completed(), priorReviews.total());

        var milestones = new ArrayList<Map<String, Object>>();
        milestones.add(milestone("applications", "投递", m.applications(), null));
        milestones.add(milestone("interviewed", "有面试记录", m.interviewed(),
                rounded(rate(m.interviewed(), m.applications()))));
        milestones.add(milestone("finalInterview", "进入终面", m.finalInterviews(),
                rounded(rate(m.finalInterviews(), m.interviewed()))));
        milestones.add(milestone("pathOffers", "终面后 Offer", m.pathOffers(),
                rounded(rate(m.pathOffers(), m.finalInterviews()))));
        Map<String, Object> biggestDrop = null;
        int biggestLoss = Integer.MIN_VALUE;
        for (int index = 1; index < milestones.size(); index++) {
            int loss = ((Number) milestones.get(index - 1).get("count")).intValue()
                    - ((Number) milestones.get(index).get("count")).intValue();
            if (loss > biggestLoss) {
                biggestLoss = loss;
                biggestDrop = Map.of("from", milestones.get(index - 1).get("label"),
                        "to", milestones.get(index).get("label"), "count", loss);
            }
        }

        List<Map<String, Object>> types = dimensions(current.types(), true);
        List<Dimension> cityRows = new ArrayList<>(current.cities());
        cityRows.sort(Comparator.comparingInt(Dimension::applications).reversed()
                .thenComparing(Dimension::key));
        if (cityRows.size() > 8) {
            var visible = new ArrayList<>(cityRows.subList(0, 8));
            int applications = 0, interviewed = 0, offers = 0;
            for (Dimension row : cityRows.subList(8, cityRows.size())) {
                applications += row.applications();
                interviewed += row.interviewed();
                offers += row.offers();
            }
            visible.add(new Dimension("__other__", applications, interviewed, offers));
            cityRows = visible;
        }
        List<Map<String, Object>> cities = dimensions(cityRows, false);
        boolean sufficient = m.applications() >= 5;
        List<String> summary = new ArrayList<>();
        if (!sufficient && m.applications() > 0) {
            summary.add("当前仅有少量投递记录，先积累更多样本再判断维度差异。");
        }
        if (p != null && p.applications() > 0) {
            Double change = (Double) comparison((double) m.applications(),
                    (double) p.applications(), "percent").get("delta");
            if (change != null) {
                summary.add("本周期投递量较上一周期" + (change >= 0 ? "增加" : "减少")
                        + " " + numberText(Math.abs(change)) + "%。");
            }
        }
        if (sufficient && biggestLoss != 0 && biggestDrop != null) {
            summary.add("记录中的最大流失发生在“" + biggestDrop.get("from") + " → "
                    + biggestDrop.get("to") + "”，共减少 " + biggestLoss + " 个机会。");
        }
        if (sufficient) {
            var eligible = new ArrayList<Map<String, Object>>();
            eligible.addAll(types);
            eligible.addAll(cities);
            eligible.removeIf(row -> !Boolean.TRUE.equals(row.get("sampleSufficient")));
            eligible.sort(Comparator.<Map<String, Object>>comparingDouble(
                    row -> ((Number) row.get("offerRate")).doubleValue()).reversed()
                    .thenComparing(Comparator.<Map<String, Object>>comparingDouble(
                            row -> ((Number) row.get("interviewRate")).doubleValue()).reversed())
                    .thenComparing(Comparator.<Map<String, Object>>comparingInt(
                            row -> ((Number) row.get("applications")).intValue()).reversed()));
            if (!eligible.isEmpty()) {
                var best = eligible.getFirst();
                summary.add("有效样本中，“" + best.get("label") + "”的 Offer 率最高，为 "
                        + numberText(((Number) best.get("offerRate")).doubleValue()) + "%。");
            }
        }
        List<String> quality = new ArrayList<>();
        if (m.offersWithoutInterview() > 0) {
            quality.add(m.offersWithoutInterview()
                    + " 条 Offer 未记录面试阶段，已计入总体 Offer 率但未进入里程碑漏斗。");
        }
        if (m.offersWithoutFinal() > 0) {
            quality.add(m.offersWithoutFinal()
                    + " 条 Offer 已记录面试但未记录终面，因此未进入漏斗最后一步。");
        }

        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("applications", comparison((double) m.applications(),
                p == null ? null : (double) p.applications(), "percent"));
        metrics.put("interviewRate", comparison(rate(m.interviewed(), m.applications()),
                p == null ? null : rate(p.interviewed(), p.applications()), "percentage_point"));
        metrics.put("offerRate", comparison(rate(m.offers(), m.applications()),
                p == null ? null : rate(p.offers(), p.applications()), "percentage_point"));
        metrics.put("medianDaysToFirstInterview", comparison(m.medianDaysToFirstInterview(),
                p == null ? null : p.medianDaysToFirstInterview(), "percent"));
        metrics.put("reviewCompletionRate", comparison(completion, priorCompletion,
                "percentage_point"));

        var trend = current.trend().stream().map(point -> Map.of(
                "periodStart", point.periodStart().toString(),
                "label", point.periodStart().format(DateTimeFormatter.ofPattern(
                        "week".equals(range.granularity()) ? "MM/dd" : "yyyy/MM")),
                "applications", point.applications(), "interviewed", point.interviewed(),
                "offers", point.offers())).toList();
        var stageReach = new ArrayList<Map<String, Object>>();
        for (RecruitmentStage stage : RecruitmentStage.values()) {
            int count = current.stages().stream().filter(item -> item.stage().equals(stage.value()))
                    .mapToInt(AnalyticsReportAggregate.StageCount::count).findFirst().orElse(0);
            stageReach.add(Map.of("stage", stage.value(), "count", count,
                    "rate", rounded(rate(count, m.applications()))));
        }
        var byStage = current.reviewsByStage().stream().map(row -> Map.of(
                "stage", row.stage(), "total", row.total(),
                "results", Map.of("pending", row.pending(), "passed", row.passed(),
                        "failed", row.failed()))).toList();
        var interviews = Map.of("total", reviews.total(), "completed", reviews.completed(),
                "completionRate", rounded(completion == null ? 0 : completion),
                "resolved", reviews.resolved(), "passed", reviews.passed(),
                "passRate", rounded(rate(reviews.passed(), reviews.resolved())),
                "byStage", byStage);
        var report = new LinkedHashMap<String, Object>();
        report.put("query", query(range));
        report.put("availableCities", availableCities);
        report.put("metrics", metrics);
        report.put("trend", trend);
        report.put("milestones", milestones);
        report.put("biggestDrop", biggestDrop);
        report.put("stageReach", stageReach);
        report.put("typeBreakdown", types);
        report.put("cityBreakdown", cities);
        report.put("interviews", interviews);
        report.put("summary", summary);
        report.put("dataQuality", quality);
        report.put("sampleSufficient", sufficient);
        return report;
    }

    private static Map<String, Object> query(AnalyticsReportRange range) {
        var query = new LinkedHashMap<String, Object>();
        query.put("period", range.period());
        if (range.from() != null) {
            query.put("from", range.from().toString());
        }
        if (range.to() != null) {
            query.put("to", range.to().toString());
        }
        if (range.type() != null) {
            query.put("type", range.type().value());
        }
        if (range.hasCityFilter()) {
            query.put("city", range.city());
        }
        query.put("hasCityFilter", range.hasCityFilter());
        if (range.comparisonFrom() != null) {
            query.put("comparisonFrom", range.comparisonFrom().toString());
        }
        if (range.comparisonTo() != null) {
            query.put("comparisonTo", range.comparisonTo().toString());
        }
        query.put("granularity", range.granularity());
        return query;
    }

    private static Map<String, Object> milestone(String key, String label, int count,
            Double conversion) {
        var result = new LinkedHashMap<String, Object>();
        result.put("key", key);
        result.put("label", label);
        result.put("count", count);
        result.put("conversionFromPrevious", conversion);
        return result;
    }

    private static List<Map<String, Object>> dimensions(List<Dimension> rows, boolean type) {
        var result = new ArrayList<Map<String, Object>>();
        for (Dimension row : rows) {
            String label = type ? TYPE_LABELS.getOrDefault(row.key(), row.key())
                    : "__other__".equals(row.key()) ? "其他"
                    : row.key().isEmpty() ? "未填写" : row.key();
            result.add(Map.of("key", row.key(), "label", label,
                    "applications", row.applications(),
                    "interviewRate", rounded(rate(row.interviewed(), row.applications())),
                    "offerRate", rounded(rate(row.offers(), row.applications())),
                    "sampleSufficient", row.applications() >= 5));
        }
        result.sort(Comparator.<Map<String, Object>>comparingInt(
                row -> ((Number) row.get("applications")).intValue()).reversed()
                .thenComparing((left, right) -> CHINESE.compare(left.get("label"), right.get("label"))));
        return result;
    }

    private static Map<String, Object> comparison(Double value, Double previous, String kind) {
        Double delta = null;
        if (value != null && previous != null) {
            if ("percentage_point".equals(kind)) {
                delta = rounded(value - previous);
            } else if (previous == 0) {
                delta = value == 0 ? 0.0 : null;
            } else {
                delta = rounded((value - previous) / previous * 100);
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("value", value == null ? null : rounded(value));
        result.put("previous", previous);
        result.put("delta", delta);
        result.put("deltaKind", kind);
        return result;
    }

    private static double rate(int part, int total) {
        return total == 0 ? 0 : (double) part / total * 100;
    }

    private static double rounded(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String numberText(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
