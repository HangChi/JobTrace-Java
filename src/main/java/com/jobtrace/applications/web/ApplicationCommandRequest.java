package com.jobtrace.applications.web;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import com.jobtrace.applications.domain.ApplicationMutation;
import com.jobtrace.applications.domain.ApplicationMutation.Stage;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Strict legacy-compatible parsing at the HTTP boundary. Unknown fields are stripped. */
public final class ApplicationCommandRequest {

    private ApplicationCommandRequest() {}

    public static ApplicationMutation create(JsonNode body) {
        return parse(body, false);
    }

    public static ApplicationMutation update(JsonNode body) {
        return parse(body, true);
    }

    private static ApplicationMutation parse(JsonNode body, boolean update) {
        object(body);
        String company = requiredText(body, "companyName", 200);
        String position = requiredText(body, "positionName", 200);
        LocalDate appliedDate = requiredDate(body, "appliedDate");
        String type = enumValue(body, "type", ApplicationType::fromWire,
                "campus_recruitment");
        String status = enumValue(body, "status", ApplicationStatus::fromWire,
                "submitted");
        String city = optionalText(body, "city", 100);
        String notes = optionalText(body, "notes", 10000);
        String jobUrl = optionalText(body, "jobUrl", 2048);
        if (jobUrl != null && !jobUrl.isEmpty()) {
            try {
                URI uri = URI.create(jobUrl);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw invalid("jobUrl must be an HTTP URL");
                }
            } catch (IllegalArgumentException exception) {
                throw invalid("jobUrl must be an HTTP URL");
            }
        }
        List<Stage> stages = stages(body.get("stages"));
        int version = update ? positiveInt(body, "version") : 0;
        LocalDate changeDate = update ? requiredDate(body, "changeDate") : null;
        if (update && changeDate.isBefore(appliedDate)) {
            throw invalid("changeDate cannot precede appliedDate");
        }
        UUID jobMarketPostId = body.has("jobMarketPostId")
                ? uuid(body.get("jobMarketPostId")) : null;
        var payload = new LinkedHashMap<String, Object>();
        payload.put("companyName", company);
        payload.put("positionName", position);
        payload.put("appliedDate", appliedDate.toString());
        payload.put("type", type);
        payload.put("status", status);
        if (body.has("city")) {
            payload.put("city", city);
        }
        if (body.has("jobUrl")) {
            payload.put("jobUrl", jobUrl);
        }
        if (body.has("notes")) {
            payload.put("notes", notes);
        }
        payload.put("stages", stages.stream().map(stage -> Map.of(
                "stage", stage.stage().value(), "occurredOn", stage.occurredOn().toString())).toList());
        return new ApplicationMutation(company, position, appliedDate, version,
                changeDate, jobMarketPostId, List.copyOf(stages),
                java.util.Collections.unmodifiableMap(payload));
    }

    public static ApplicationStatus status(JsonNode body) {
        object(body);
        return ApplicationStatus.fromWire(requiredText(body, "status", 40))
                .orElseThrow(() -> invalid("Invalid status"));
    }

    public static int version(JsonNode body) {
        object(body);
        return positiveInt(body, "version");
    }

    public static Stage stage(JsonNode body) {
        object(body);
        RecruitmentStage value = RecruitmentStage.fromWire(requiredText(body, "stage", 40))
                .orElseThrow(() -> invalid("Invalid stage"));
        return new Stage(value, requiredDate(body, "occurredOn"));
    }

    public static LocalDate changeDate(JsonNode body) {
        object(body);
        return requiredDate(body, "changeDate");
    }

    public static List<UUID> ids(JsonNode body) {
        object(body);
        JsonNode values = body.get("ids");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 100) {
            throw invalid("ids must contain 1–100 application IDs");
        }
        var ids = new LinkedHashSet<UUID>();
        for (JsonNode value : values) {
            ids.add(uuid(value));
        }
        return List.copyOf(ids);
    }

    private static List<Stage> stages(JsonNode values) {
        if (values == null) {
            return List.of();
        }
        if (!values.isArray() || values.size() > 100) {
            throw invalid("stages must contain at most 100 items");
        }
        var result = new ArrayList<Stage>();
        for (JsonNode value : values) {
            result.add(stage(value));
        }
        return result;
    }

    private static String enumValue(JsonNode body, String name,
            java.util.function.Function<String, ? extends java.util.Optional<?>> parser,
            String defaultValue) {
        if (!body.has(name)) {
            return defaultValue;
        }
        String value = requiredText(body, name, 50);
        if (parser.apply(value).isEmpty()) {
            throw invalid("Invalid " + name);
        }
        return value;
    }

    private static String requiredText(JsonNode body, String name, int max) {
        JsonNode value = body.get(name);
        if (value == null || !value.isTextual()) {
            throw invalid(name + " must be text");
        }
        String text = value.textValue().trim();
        if (text.isEmpty() || text.length() > max) {
            throw invalid(name + " has invalid length");
        }
        return text;
    }

    private static String optionalText(JsonNode body, String name, int max) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(name + " must be text or null");
        }
        String text = value.textValue().trim();
        if (text.length() > max) {
            throw invalid(name + " is too long");
        }
        return text;
    }

    private static LocalDate requiredDate(JsonNode body, String name) {
        String value = requiredText(body, name, 10);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(name + " must be an ISO date");
        }
    }

    private static int positiveInt(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || !value.isInt() || value.intValue() < 1) {
            throw invalid(name + " must be a positive integer");
        }
        return value.intValue();
    }

    private static UUID uuid(JsonNode value) {
        if (value == null || !value.isTextual()) {
            throw invalid("Invalid UUID");
        }
        try {
            return UUID.fromString(value.textValue());
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid UUID");
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
