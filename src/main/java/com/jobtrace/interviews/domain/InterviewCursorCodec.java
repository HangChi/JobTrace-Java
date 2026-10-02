package com.jobtrace.interviews.domain;

import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Legacy base64url cursor; navigation data only, never an authorization claim. */
public final class InterviewCursorCodec {

    private final ObjectMapper mapper;

    public InterviewCursorCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(Cursor cursor) {
        Objects.requireNonNull(cursor);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("value", cursor.value().toString());
        value.put("id", cursor.id());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(value));
    }

    public Cursor decode(String encoded) {
        try {
            JsonNode value = mapper.readTree(Base64.getUrlDecoder().decode(encoded));
            if (!value.isObject() || !value.hasNonNull("value") || !value.hasNonNull("id")
                    || !value.get("value").isTextual() || !value.get("id").isTextual()) {
                throw new IllegalArgumentException("Cursor is incomplete");
            }
            return new Cursor(LocalDate.parse(value.get("value").textValue()),
                    UUID.fromString(value.get("id").textValue()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Cursor is invalid", exception);
        }
    }

    public record Cursor(LocalDate value, UUID id) {
        public Cursor {
            Objects.requireNonNull(value);
            Objects.requireNonNull(id);
        }
    }
}
