package com.jobtrace.admin.domain;

import java.util.List;
import java.util.Objects;

public record AdminSummary(
        String generatedAt,
        String timeZone,
        String activityDefinition,
        CountsSection counts,
        ActivitySection activity) {

    public record Counts(int users, int activeUsers, int disabledUsers, int administrators,
                         int applications, int interviews) {}

    public record ActivityWindows(int registered7d, int active7d,
                                  int registered30d, int active30d) {}

    public record ActivityPoint(String date, int registeredUsers, int activeUsers) {}

    public record Activity(ActivityWindows windows, List<ActivityPoint> dailyTrend) {
        public Activity {
            dailyTrend = List.copyOf(Objects.requireNonNull(dailyTrend));
        }
    }

    public record CountsSection(String status, Counts value) {
        public static CountsSection available(Counts value) {
            return new CountsSection("available", Objects.requireNonNull(value));
        }

        public static CountsSection unavailable() {
            return new CountsSection("unavailable", null);
        }
    }

    public record ActivitySection(
            String status, ActivityWindows windows, List<ActivityPoint> dailyTrend) {
        public ActivitySection {
            dailyTrend = dailyTrend == null ? null : List.copyOf(dailyTrend);
        }

        public static ActivitySection available(Activity value) {
            Objects.requireNonNull(value);
            return new ActivitySection("available", value.windows(), value.dailyTrend());
        }

        public static ActivitySection unavailable() {
            return new ActivitySection("unavailable", null, null);
        }
    }
}
