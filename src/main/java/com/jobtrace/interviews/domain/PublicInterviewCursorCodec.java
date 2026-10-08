package com.jobtrace.interviews.domain;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Legacy base64url cursor with a timestamp or score|timestamp value. */
public final class PublicInterviewCursorCodec {

    private final ObjectMapper mapper;

    public PublicInterviewCursorCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(Cursor cursor) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mapper.writeValueAsBytes(Map.of("value", cursor.value(), "id", cursor.id())));
    }

    public Cursor decode(String encoded, PublicInterviewCriteria.Sort sort) {
        try {
            JsonNode json = mapper.readTree(Base64.getUrlDecoder().decode(encoded));
            if (!json.isObject() || !json.hasNonNull("value") || !json.hasNonNull("id")
                    || !json.get("value").isTextual() || !json.get("id").isTextual()) {
                throw new IllegalArgumentException("Cursor is incomplete");
            }
            String value = json.get("value").textValue();
            UUID id = UUID.fromString(json.get("id").textValue());
            if (sort == PublicInterviewCriteria.Sort.HOT) {
                int separator = value.indexOf('|');
                if (separator < 1) {
                    throw new IllegalArgumentException("Hot cursor is incomplete");
                }
                long score = Long.parseLong(value.substring(0, separator));
                Instant publishedAt = Instant.parse(value.substring(separator + 1));
                return new Cursor(value, id, publishedAt, score);
            }
            return new Cursor(value, id, Instant.parse(value), null);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Cursor is invalid", exception);
        }
    }

    public record Cursor(String value, UUID id, Instant publishedAt, Long score) {}
}
