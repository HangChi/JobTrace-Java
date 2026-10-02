package com.jobtrace.applications.domain;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ApplicationCursorCodec {

    private final ObjectMapper objectMapper;

    public ApplicationCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Cursor cursor) {
        Objects.requireNonNull(cursor);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("value", cursor.value());
        value.put("id", cursor.id());
        if (cursor.statusRank() != null) {
            value.put("statusRank", cursor.statusRank());
        }
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor could not be encoded", exception);
        }
    }

    public Cursor decode(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            JsonNode value = objectMapper.readTree(decoded);
            if (!value.isObject()
                    || !value.hasNonNull("value")
                    || !value.hasNonNull("id")
                    || !value.get("value").isTextual()
                    || !value.get("id").isTextual()
                    || value.get("value").textValue().isEmpty()) {
                throw new IllegalArgumentException("cursor is incomplete");
            }
            if (value.hasNonNull("statusRank")
                    && (!value.get("statusRank").isIntegralNumber()
                    || !value.get("statusRank").canConvertToInt())) {
                throw new IllegalArgumentException("cursor rank is invalid");
            }
            Integer rank = value.has("statusRank") && !value.get("statusRank").isNull()
                    ? value.get("statusRank").intValue()
                    : null;
            return new Cursor(
                    value.get("value").textValue(),
                    UUID.fromString(value.get("id").textValue()),
                    rank);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }

    public record Cursor(String value, UUID id, Integer statusRank) {
        public Cursor {
            if (value == null || value.isEmpty() || id == null) {
                throw new IllegalArgumentException("cursor value and id are required");
            }
            if (statusRank != null && (statusRank < 0 || statusRank > 2)) {
                throw new IllegalArgumentException("cursor status rank is invalid");
            }
        }
    }
}
