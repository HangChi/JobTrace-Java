package com.jobtrace.reminders.web;

import com.jobtrace.reminders.domain.ReminderMutation.Create;
import com.jobtrace.reminders.domain.ReminderMutation.Update;
import com.jobtrace.reminders.domain.ReminderMutation.Transition;
import com.jobtrace.reminders.domain.ReminderOperationException;
import com.jobtrace.reminders.domain.ReminderOperationException.Kind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Parses the legacy reminder payload with no caller-supplied owner. */
public final class ReminderCommandRequest {

    private ReminderCommandRequest() {}

    public static Create create(JsonNode body) {
        object(body);
        return new Create(uuid(body, "applicationId", false),
                uuid(body, "sourceStageOccurrenceId", true),
                title(body), instant(body, "eventAt"), instant(body, "notifyAt"),
                emailEnabled(body, false));
    }

    public static Update update(JsonNode body) {
        Create value = createRequiredEmail(body);
        return new Update(value, version(body));
    }

    public static Transition versionOnly(JsonNode body, String action) {
        return new Transition(version(body), action, null, null);
    }

    public static Transition snooze(JsonNode body) {
        return new Transition(version(body), "snooze", null, instant(body, "notifyAt"));
    }

    public static Transition reopen(JsonNode body) {
        return new Transition(version(body), "reopen", instant(body, "eventAt"),
                instant(body, "notifyAt"));
    }

    public static int version(JsonNode body) {
        object(body);
        JsonNode value = body.get("version");
        if (value == null || !value.isIntegralNumber()
                || !value.toString().matches("[1-9][0-9]{0,9}")
                || Long.parseLong(value.toString()) > Integer.MAX_VALUE) {
            throw invalid("version 必须为正整数。");
        }
        return value.intValue();
    }

    private static Create createRequiredEmail(JsonNode body) {
        object(body);
        return new Create(uuid(body, "applicationId", false),
                uuid(body, "sourceStageOccurrenceId", true),
                title(body), instant(body, "eventAt"), instant(body, "notifyAt"),
                emailEnabled(body, true));
    }

    private static boolean emailEnabled(JsonNode body, boolean required) {
        JsonNode value = body.get("emailEnabled");
        if (value == null && !required) {
            return false;
        }
        if (value == null || !value.isBoolean()) {
            throw invalid("emailEnabled 必须为布尔值。");
        }
        return value.booleanValue();
    }

    private static String title(JsonNode body) {
        JsonNode value = body.get("title");
        if (value == null || !value.isTextual()) {
            throw invalid("请输入待办内容。");
        }
        String title = value.textValue().trim();
        if (title.isEmpty() || title.length() > 120) {
            throw invalid("请输入 1–120 字的待办内容。");
        }
        return title;
    }

    private static Instant instant(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid("请填写有效的日期和时间。");
        }
        try {
            return OffsetDateTime.parse(value.textValue()).toInstant();
        } catch (DateTimeParseException exception) {
            throw invalid("请填写有效的日期和时间。");
        }
    }

    private static UUID uuid(JsonNode body, String field, boolean nullable) {
        JsonNode value = body.get(field);
        if (nullable && (value == null || value.isNull())) {
            return null;
        }
        if (value == null || !value.isTextual()) {
            throw invalid(field + " 必须为有效 ID。");
        }
        try {
            return UUID.fromString(value.textValue());
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " 必须为有效 ID。");
        }
    }

    private static void object(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("请求内容无效。");
        }
    }

    private static ReminderOperationException invalid(String message) {
        return new ReminderOperationException(Kind.VALIDATION, message);
    }
}
