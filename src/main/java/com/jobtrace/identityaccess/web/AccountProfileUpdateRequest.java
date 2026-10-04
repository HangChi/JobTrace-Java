package com.jobtrace.identityaccess.web;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Strict known-field parsing while preserving legacy unknown-field stripping. */
public record AccountProfileUpdateRequest(String displayName, String image) {

    public static AccountProfileUpdateRequest from(Map<String, JsonNode> values) {
        if (values == null) {
            throw new IllegalArgumentException("request body is required");
        }
        JsonNode name = values.get("displayName");
        if (name == null || !name.isTextual()) {
            throw new IllegalArgumentException("displayName must be a string");
        }
        JsonNode image = values.get("image");
        if (image == null || image.isNull()) {
            return new AccountProfileUpdateRequest(name.textValue(), null);
        }
        if (!image.isTextual()) {
            throw new IllegalArgumentException("image must be a string or null");
        }
        return new AccountProfileUpdateRequest(name.textValue(), image.textValue());
    }
}
