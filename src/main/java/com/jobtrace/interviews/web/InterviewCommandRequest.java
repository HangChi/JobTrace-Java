package com.jobtrace.interviews.web;

import com.jobtrace.interviews.domain.InterviewCatalog.AuthorMode;
import com.jobtrace.interviews.domain.InterviewCatalog.Format;
import com.jobtrace.interviews.domain.InterviewCatalog.QuestionCategory;
import com.jobtrace.interviews.domain.InterviewCatalog.Result;
import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import com.jobtrace.interviews.domain.InterviewCatalog.Visibility;
import com.jobtrace.interviews.domain.InterviewMutation.Create;
import com.jobtrace.interviews.domain.InterviewMutation.Update;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/** Strict legacy-compatible HTTP input parser; never forwards unknown fields to SQL. */
public final class InterviewCommandRequest {

    private InterviewCommandRequest() {}

    public static Create create(JsonNode body) {
        object(body);
        UUID applicationId = uuid(body.get("applicationId"), "applicationId");
        UUID occurrenceId = nullableUuid(body, "stageOccurrenceId");
        String stage = optionalEnum(body, "stage", Stage::fromWire);
        LocalDate interviewedOn = optionalDate(body, "interviewedOn");
        LocalDate stageOccurredOn = optionalDate(body, "stageOccurredOn");
        if ((occurrenceId != null) == (stage != null && interviewedOn != null)) {
            throw invalid("请选择已有面试阶段，或填写新的轮次和日期");
        }
        if (occurrenceId != null && (stage != null || stageOccurredOn != null)) {
            throw invalid("选择已有阶段时不能另行指定阶段日期");
        }
        var payload = new LinkedHashMap<String, Object>();
        putIfPresent(payload, body, "stageOccurredOn", stageOccurredOn == null ? null : stageOccurredOn.toString());
        putIfPresent(payload, body, "format", optionalEnum(body, "format", Format::fromWire));
        putIfPresent(payload, body, "durationMinutes", optionalInt(body, "durationMinutes", 1, 600));
        putIfPresent(payload, body, "interviewerNotes", optionalText(body, "interviewerNotes", 2000));
        payload.put("roundResult", enumOrDefault(body, "roundResult", Result::fromWire, "pending"));
        return new Create(applicationId, occurrenceId, stage, interviewedOn,
                Collections.unmodifiableMap(payload));
    }

    public static Update update(JsonNode body) {
        object(body);
        int version = requiredInt(body, "version", 1, Integer.MAX_VALUE);
        UUID sessionId = nullableUuid(body, "autosaveSessionId");
        Integer revision = optionalInt(body, "autosaveRevision", 1, Integer.MAX_VALUE);
        if ((sessionId == null) != (revision == null)) {
            throw invalid("自动保存会话和修订号必须同时提供");
        }
        var payload = new LinkedHashMap<String, Object>();
        if (sessionId != null) {
            payload.put("autosaveSessionId", sessionId.toString());
            payload.put("autosaveRevision", revision);
        }
        LocalDate date = optionalDate(body, "interviewedOn");
        putIfPresent(payload, body, "interviewedOn", date == null ? null : date.toString());
        putIfPresent(payload, body, "format", optionalEnum(body, "format", Format::fromWire));
        putIfPresent(payload, body, "durationMinutes", optionalInt(body, "durationMinutes", 1, 600));
        for (var field : List.of("interviewerNotes", "highlights", "gaps")) {
            putIfPresent(payload, body, field, optionalText(body, field,
                    "interviewerNotes".equals(field) ? 2000 : 10000));
        }
        payload.put("roundResult", enumOrDefault(body, "roundResult", Result::fromWire, "pending"));
        String status = enumOrDefault(body, "status", Status::fromWire, "draft");
        payload.put("status", status);
        String visibility = optionalEnum(body, "visibility", Visibility::fromWire);
        putIfPresent(payload, body, "visibility", visibility);
        putIfPresent(payload, body, "authorMode", optionalEnum(body, "authorMode", AuthorMode::fromWire));
        List<Map<String, Object>> questions = questions(body.get("questions"));
        payload.put("questions", questions);
        payload.put("actionItems", actions(body.get("actionItems")));
        if ("completed".equals(status) && questions.isEmpty()) {
            throw invalid("请先填写面经内容，再完成复盘。");
        }
        if ("public".equals(visibility) && !"completed".equals(status)) {
            throw invalid("请先完成复盘，再公开面经。");
        }
        return new Update(version, Collections.unmodifiableMap(payload));
    }

    private static List<Map<String, Object>> questions(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray() || node.size() > 200) {
            throw invalid("questions must be an array of at most 200");
        }
        var result = new ArrayList<Map<String, Object>>();
        for (JsonNode item : node) {
            object(item);
            var entry = new LinkedHashMap<String, Object>();
            UUID id = nullableUuid(item, "id");
            if (id != null) {
                entry.put("id", id.toString());
            }
            entry.put("category", enumOrDefault(item, "category", QuestionCategory::fromWire, "other"));
            entry.put("question", requiredText(item, "question", 4000));
            for (var name : List.of("originalAnswer", "followUpNotes", "improvedAnswer")) {
                putIfPresent(entry, item, name, optionalText(item, name, 10000));
            }
            putIfPresent(entry, item, "selfRating", optionalInt(item, "selfRating", 1, 5));
            result.add(Collections.unmodifiableMap(entry));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> actions(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray() || node.size() > 100) {
            throw invalid("actionItems must be an array of at most 100");
        }
        var result = new ArrayList<Map<String, Object>>();
        for (JsonNode item : node) {
            object(item);
            var entry = new LinkedHashMap<String, Object>();
            UUID id = nullableUuid(item, "id");
            if (id != null) {
                entry.put("id", id.toString());
            }
            entry.put("content", requiredText(item, "content", 1000));
            JsonNode completed = item.get("completed");
            if (completed != null && !completed.isBoolean()) {
                throw invalid("completed must be boolean");
            }
            entry.put("completed", completed != null && completed.booleanValue());
            result.add(Collections.unmodifiableMap(entry));
        }
        return List.copyOf(result);
    }

    private static void putIfPresent(Map<String, Object> target, JsonNode source,
            String field, Object value) {
        if (source.has(field)) {
            target.put(field, value);
        }
    }

    private static String requiredText(JsonNode body, String field, int max) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            throw invalid(field + " must be text");
        }
        String value = node.textValue().trim();
        if (value.isEmpty() || value.length() > max) {
            throw invalid(field + " has invalid length");
        }
        return value;
    }

    private static String optionalText(JsonNode body, String field, int max) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(field + " must be text or null");
        }
        String value = node.textValue().trim();
        if (value.length() > max) {
            throw invalid(field + " is too long");
        }
        return value;
    }

    private static <T> String optionalEnum(JsonNode body, String field,
            Function<String, Optional<T>> parser) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = requiredText(body, field, 50);
        if (parser.apply(value).isEmpty()) {
            throw invalid("Invalid " + field);
        }
        return value;
    }

    private static <T> String enumOrDefault(JsonNode body, String field,
            Function<String, Optional<T>> parser, String fallback) {
        if (!body.has(field)) {
            return fallback;
        }
        String value = optionalEnum(body, field, parser);
        if (value == null) {
            throw invalid("Invalid " + field);
        }
        return value;
    }

    private static LocalDate optionalDate(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return LocalDate.parse(requiredText(body, field, 10));
        } catch (DateTimeParseException exception) {
            throw invalid(field + " must be an ISO date");
        }
    }

    private static Integer optionalInt(JsonNode body, String field, int min, int max) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isInt() || node.intValue() < min || node.intValue() > max) {
            throw invalid(field + " is out of range");
        }
        return node.intValue();
    }

    private static int requiredInt(JsonNode body, String field, int min, int max) {
        Integer value = optionalInt(body, field, min, max);
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static UUID nullableUuid(JsonNode body, String field) {
        JsonNode value = body.get(field);
        return value == null || value.isNull() ? null : uuid(value, field);
    }

    private static UUID uuid(JsonNode value, String field) {
        if (value == null || !value.isTextual()) {
            throw invalid("Invalid " + field);
        }
        try {
            return UUID.fromString(value.textValue());
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid " + field);
        }
    }

    private static void object(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("JSON object required");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
