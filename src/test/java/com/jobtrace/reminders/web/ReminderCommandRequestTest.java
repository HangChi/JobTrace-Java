package com.jobtrace.reminders.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.reminders.domain.ReminderOperationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ReminderCommandRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String VALID = """
            {"applicationId":"00000000-0000-0000-0000-000000000201",
             "title":"  Follow up  ","eventAt":"2030-10-03T12:00:00+08:00",
             "notifyAt":"2030-10-03T10:00:00+08:00"}
            """;

    @Test
    void appliesCreateDefaultsAndParsesExplicitOffset() {
        var value = ReminderCommandRequest.create(json(VALID));
        assertThat(value.title()).isEqualTo("Follow up");
        assertThat(value.emailEnabled()).isFalse();
        assertThat(value.sourceStageOccurrenceId()).isNull();
        assertThat(value.eventAt().toString()).isEqualTo("2030-10-03T04:00:00Z");
    }

    @Test
    void rejectsMalformedCreateFieldsBeforeStorage() {
        for (String payload : new String[] {
                "[]", "{}", VALID.replace("00000000-0000-0000-0000-000000000201", "bad"),
                VALID.replace("\"title\":\"  Follow up  \"", "\"title\":42"),
                VALID.replace("  Follow up  ", "   "),
                VALID.replace("  Follow up  ", "a".repeat(121)),
                VALID.replace("2030-10-03T12:00:00+08:00", "not-a-date"),
                VALID.replace("\"notifyAt\":\"2030-10-03T10:00:00+08:00\"",
                        "\"notifyAt\":null"),
                VALID.replace("\"title\"", "\"sourceStageOccurrenceId\":42,\"title\""),
                VALID.replace("\"title\"", "\"emailEnabled\":null,\"title\"")}) {
            assertThatThrownBy(() -> ReminderCommandRequest.create(json(payload)))
                    .isInstanceOf(ReminderOperationException.class);
        }
    }

    @Test
    void rejectsMissingBooleanAndInvalidVersionsIncludingHugeInteger() {
        assertThatThrownBy(() -> ReminderCommandRequest.update(json(VALID)))
                .isInstanceOf(ReminderOperationException.class);
        for (String value : new String[] {"0", "-1", "1.5", "\"1\"",
                "2147483648", "18446744073709551617"}) {
            assertThatThrownBy(() -> ReminderCommandRequest.version(
                    json("{\"version\":" + value + "}")))
                    .isInstanceOf(ReminderOperationException.class);
        }
        assertThat(ReminderCommandRequest.version(json("{\"version\":2147483647}")))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void parsesTransitionPayloads() {
        assertThat(ReminderCommandRequest.versionOnly(json("{\"version\":2}"), "complete")
                .action()).isEqualTo("complete");
        assertThat(ReminderCommandRequest.snooze(json("""
                {"version":3,"notifyAt":"2030-10-03T10:00:00Z"}
                """)).notifyAt()).isNotNull();
        assertThat(ReminderCommandRequest.reopen(json("""
                {"version":4,"eventAt":"2030-10-03T12:00:00Z",
                 "notifyAt":"2030-10-03T10:00:00Z"}
                """)).eventAt()).isNotNull();
    }

    private JsonNode json(String value) {
        return mapper.readTree(value);
    }
}
