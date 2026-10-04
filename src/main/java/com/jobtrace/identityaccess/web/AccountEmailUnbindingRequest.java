package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Parses the sole required field and ignores unrelated object properties. */
public record AccountEmailUnbindingRequest(JsonNode currentPassword) {

    public static AccountEmailUnbindingRequest from(Map<String, JsonNode> values) {
        if (values == null) {
            throw invalid();
        }
        return new AccountEmailUnbindingRequest(values.get("currentPassword"));
    }

    public String currentPasswordValue() {
        if (currentPassword == null || !currentPassword.isTextual()) {
            throw invalid();
        }
        return currentPassword.textValue();
    }

    private static AccountPasswordValidationException invalid() {
        return new AccountPasswordValidationException(
                "currentPassword", "请输入当前密码。");
    }
}
