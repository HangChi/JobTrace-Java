package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.domain.AccountEmailBindingValidationException;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Parses the three required fields without exposing unrelated request properties. */
public record AccountEmailBindingRequest(
        JsonNode email, JsonNode verificationCode, JsonNode currentPassword) {

    public static AccountEmailBindingRequest from(Map<String, JsonNode> values) {
        if (values == null) {
            throw invalid("email");
        }
        return new AccountEmailBindingRequest(
                values.get("email"), values.get("verificationCode"),
                values.get("currentPassword"));
    }

    public String emailValue() {
        return text(email, "email");
    }

    public String codeValue() {
        return text(verificationCode, "verificationCode");
    }

    public String passwordValue() {
        return text(currentPassword, "currentPassword");
    }

    private static String text(JsonNode value, String field) {
        if (value == null || !value.isTextual()) {
            throw invalid(field);
        }
        return value.textValue();
    }

    private static AccountEmailBindingValidationException invalid(String field) {
        return new AccountEmailBindingValidationException(field, "无效的邮箱绑定字段。");
    }
}
