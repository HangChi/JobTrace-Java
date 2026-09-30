package com.jobtrace.shared.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

public final class SafeLogger {

    static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "authorization",
            "cookie",
            "email",
            "ip",
            "password",
            "secret",
            "session",
            "token",
            "useragent");

    private SafeLogger() {
    }

    public static void info(Logger logger, String event, Map<String, ?> fields) {
        logger.info("event={} fields={}", event, sanitize(fields));
    }

    static Map<String, Object> sanitize(Map<String, ?> fields) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        fields.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return Map.copyOf(sanitized);
    }

    private static Object sanitizeValue(String key, Object value) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> stringKeyed = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) ->
                    stringKeyed.put(String.valueOf(nestedKey), nestedValue));
            return sanitize(stringKeyed);
        }
        return value;
    }
}

