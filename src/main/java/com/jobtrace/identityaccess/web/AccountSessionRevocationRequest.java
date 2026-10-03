package com.jobtrace.identityaccess.web;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Strict transport model that prevents Jackson scalar coercion for the opaque ID. */
public record AccountSessionRevocationRequest(JsonNode id) {

    public static AccountSessionRevocationRequest from(Map<String, JsonNode> values) {
        if (values == null || values.size() != 1 || !values.containsKey("id")) {
            throw new IllegalArgumentException("request must contain only id");
        }
        return new AccountSessionRevocationRequest(values.get("id"));
    }

    public String sessionId() {
        if (id == null || !id.isTextual()) {
            throw new IllegalArgumentException("id must be a string");
        }
        return id.textValue();
    }
}
