package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Strict password-field parser that ignores unrelated object properties. */
public record AccountPasswordChangeRequest(JsonNode currentPassword, JsonNode newPassword) {

    public static AccountPasswordChangeRequest from(Map<String, JsonNode> values) {
        if (values == null) {
            throw validation("currentPassword");
        }
        return new AccountPasswordChangeRequest(
                values.get("currentPassword"), values.get("newPassword"));
    }

    public String currentPasswordValue() {
        return text(currentPassword, "currentPassword");
    }

    public String newPasswordValue() {
        return text(newPassword, "newPassword");
    }

    private static String text(JsonNode value, String field) {
        if (value == null || !value.isTextual()) {
            throw validation(field);
        }
        return value.textValue();
    }

    private static AccountPasswordValidationException validation(String field) {
        return new AccountPasswordValidationException(field, "无效的密码字段。");
    }
}
