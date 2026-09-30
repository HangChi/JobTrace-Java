package com.jobtrace.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class SafeLoggerTest {

    @Test
    void redactsSensitiveFieldsAtEveryLevel() {
        Map<String, Object> sanitized = SafeLogger.sanitize(Map.of(
                "requestId", "req-1",
                "password", "do-not-log",
                "nested", Map.of(
                        "profileEmail", "person@example.com",
                        "count", 2)));

        assertThat(sanitized)
                .containsEntry("requestId", "req-1")
                .containsEntry("password", SafeLogger.REDACTED);
        assertThat(sanitized.get("nested"))
                .isEqualTo(Map.of("profileEmail", SafeLogger.REDACTED, "count", 2));
    }

    @Test
    void logsOnlyTheSanitizedFieldMap() {
        Logger logger = mock(Logger.class);
        Map<String, Object> input = Map.of("sessionToken", "secret", "count", 3);
        Map<String, Object> expected = Map.of("sessionToken", SafeLogger.REDACTED, "count", 3);

        SafeLogger.info(logger, "sync", input);

        verify(logger).info("event={} fields={}", "sync", expected);
    }
}

