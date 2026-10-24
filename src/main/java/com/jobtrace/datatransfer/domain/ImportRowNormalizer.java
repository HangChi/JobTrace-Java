package com.jobtrace.datatransfer.domain;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import com.jobtrace.datatransfer.domain.ImportRow.ImportError;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Legacy import aliases and row rules, independent of transport and storage. */
public final class ImportRowNormalizer {

    private static final Map<String, List<String>> ALIASES = Map.of(
            "companyName", List.of("companyName", "公司", "公司名称"),
            "positionName", List.of("positionName", "岗位", "岗位名称"),
            "appliedDate", List.of("appliedDate", "投递日期"),
            "type", List.of("type", "类型"),
            "city", List.of("city", "城市"),
            "jobUrl", List.of("jobUrl", "职位链接"),
            "notes", List.of("notes", "备注"),
            "status", List.of("status", "状态", "投递"),
            "stages", List.of("stages", "阶段", "阶段历史"));
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "暑期实习", "summer_internship", "日常实习", "daily_internship",
            "春招", "spring_recruitment", "秋招提前批", "early_campus_recruitment",
            "秋招", "campus_recruitment", "社招", "social_recruitment");
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "Offer", "offer", "已投递", "submitted", "拒绝", "refused");
    private static final Map<String, String> STAGE_LABELS = Map.of(
            "简历筛选", "screening", "测评/AI测评", "assessment", "笔试", "written_test",
            "一面/AI面", "interview_1", "二面", "interview_2", "三面", "interview_3",
            "HR 面", "hr_interview", "终面", "final_interview");
    private static final Pattern STAGE_ENTRY = Pattern.compile("^(.+?)\\s*(?:\\+|@)\\s*(\\d{4}-\\d{2}-\\d{2})$");

    private ImportRowNormalizer() {}

    public static Map<String, String> inferMapping(List<String> headers) {
        var result = new LinkedHashMap<String, String>();
        for (String header : headers) {
            result.put(header, ALIASES.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(header))
                    .map(Map.Entry::getKey).findFirst().orElse(""));
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, String> validateMapping(
            List<String> headers, Map<String, String> mapping) {
        if (mapping == null) {
            return inferMapping(headers);
        }
        Set<String> known = new LinkedHashSet<>(headers);
        Set<String> targets = new LinkedHashSet<>();
        for (var entry : mapping.entrySet()) {
            if (!known.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown source column: " + entry.getKey());
            }
            String field = entry.getValue();
            if (field == null || (!field.isEmpty() && !ALIASES.containsKey(field))) {
                throw new IllegalArgumentException("Unknown import field");
            }
            if (!field.isEmpty() && !targets.add(field)) {
                throw new IllegalArgumentException("A business field may be mapped only once");
            }
        }
        var result = new LinkedHashMap<String, String>();
        headers.forEach(header -> result.put(header, mapping.getOrDefault(header, "")));
        return Collections.unmodifiableMap(result);
    }

    public static ImportRow normalize(int rowNumber, Map<String, String> source,
            Map<String, String> mapping) {
        var raw = new LinkedHashMap<String, String>();
        mapping.forEach((header, target) -> {
            if (!target.isEmpty()) {
                raw.put(target, source.getOrDefault(header, "").trim());
            }
        });
        var errors = new ArrayList<ImportError>();
        var data = new LinkedHashMap<String, Object>();
        required(raw, data, errors, "companyName", 200);
        required(raw, data, errors, "positionName", 200);
        String date = raw.get("appliedDate");
        if (date == null || !validDate(date)) {
            errors.add(error("appliedDate", "invalid_format", "投递日期必须是 YYYY-MM-DD。"));
        } else {
            data.put("appliedDate", date);
        }
        String type = TYPE_LABELS.getOrDefault(raw.getOrDefault("type", ""),
                raw.getOrDefault("type", ""));
        if (type.isEmpty()) {
            type = "campus_recruitment";
        }
        if (ApplicationType.fromWire(type).isEmpty()) {
            errors.add(error("type", "invalid_value", "类型无效。"));
        } else {
            data.put("type", type);
        }
        String status = STATUS_LABELS.getOrDefault(raw.getOrDefault("status", ""),
                raw.getOrDefault("status", ""));
        if (status.isEmpty()) {
            status = "submitted";
        }
        if (ApplicationStatus.fromWire(status).isEmpty()) {
            errors.add(error("status", "invalid_value", "状态无效。"));
        } else {
            data.put("status", status);
        }
        optional(raw, data, errors, "city", 100);
        optional(raw, data, errors, "notes", 10000);
        optional(raw, data, errors, "jobUrl", 2048);
        Object jobUrl = data.get("jobUrl");
        if (jobUrl instanceof String url && !url.isEmpty() && !webUrl(url)) {
            errors.add(error("jobUrl", "invalid_format", "职位链接必须是 HTTP(S) 地址。"));
        }
        data.put("stages", stages(raw.get("stages"), errors));
        return new ImportRow(rowNumber, errors.isEmpty() ? Collections.unmodifiableMap(data) : null,
                List.copyOf(errors), List.of());
    }

    private static void required(Map<String, String> raw, Map<String, Object> data,
            List<ImportError> errors, String key, int max) {
        String value = raw.getOrDefault(key, "");
        if (value.isEmpty() || value.length() > max) {
            errors.add(error(key, value.isEmpty() ? "too_small" : "too_big",
                    key + " 必须为 1–" + max + " 个字符。"));
        } else {
            data.put(key, value);
        }
    }

    private static void optional(Map<String, String> raw, Map<String, Object> data,
            List<ImportError> errors, String key, int max) {
        if (!raw.containsKey(key)) {
            return;
        }
        String value = raw.get(key);
        if (value.length() > max) {
            errors.add(error(key, "too_big", key + " 不得超过 " + max + " 个字符。"));
        } else {
            data.put(key, value.isEmpty() ? null : value);
        }
    }

    private static List<Map<String, String>> stages(String value, List<ImportError> errors) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] entries = value.split("[；;\\n]");
        var stages = new ArrayList<Map<String, String>>();
        for (String entry : entries) {
            if (entry.isBlank()) {
                continue;
            }
            var matcher = STAGE_ENTRY.matcher(entry.trim());
            if (!matcher.matches() || !validDate(matcher.group(2))) {
                errors.add(error("stages", "invalid_format", "阶段历史格式无效。"));
                continue;
            }
            String descriptor = matcher.group(1).trim();
            String code = descriptor.split("/", 2)[0].trim();
            String stage = RecruitmentStage.fromWire(code).isPresent()
                    ? code : STAGE_LABELS.get(descriptor);
            if (stage == null) {
                errors.add(error("stages", "invalid_value", "阶段名称无效。"));
                continue;
            }
            stages.add(Map.of("stage", stage, "occurredOn", matcher.group(2)));
        }
        if (stages.size() > 100) {
            errors.add(error("stages", "too_big", "阶段历史不得超过 100 项。"));
        }
        return List.copyOf(stages);
    }

    private static boolean validDate(String value) {
        try {
            return value.matches("\\d{4}-\\d{2}-\\d{2}")
                    && LocalDate.parse(value).toString().equals(value);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean webUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static ImportError error(String field, String code, String message) {
        return new ImportError(field, code, message);
    }
}
