package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.web.InterviewCommandRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InterviewCommandRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createRequiresExactlyOneStageChoiceAndPreservesDefaults() {
        var command = InterviewCommandRequest.create(body("""
                {"applicationId":"00000000-0000-0000-0000-000000000201",
                 "stage":"interview_1","interviewedOn":"2026-09-18"}
                """));
        assertThat(command.stageOccurrenceId()).isNull();
        assertThat(command.stage()).isEqualTo("interview_1");
        assertThat(command.payload()).containsEntry("roundResult", "pending");
        assertThatThrownBy(() -> InterviewCommandRequest.create(body("""
                {"applicationId":"00000000-0000-0000-0000-000000000201"}
                """))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterviewCommandRequest.create(body("""
                {"applicationId":"00000000-0000-0000-0000-000000000201",
                 "stageOccurrenceId":"00000000-0000-0000-0000-000000000301",
                 "stage":"interview_1","interviewedOn":"2026-09-18"}
                """))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editValidatesNestedContentAndAutosavePair() {
        var command = InterviewCommandRequest.update(body("""
                {"version":2,"status":"completed","questions":[
                   {"category":"technical","question":"  Thread safety  ","selfRating":5}],
                 "actionItems":[{"content":"  Practice  ","completed":true}]}
                """));
        assertThat(command.version()).isEqualTo(2);
        assertThat(command.payload()).containsEntry("status", "completed");
        assertThat(command.payload().toString()).contains("Thread safety", "Practice");
        for (String json : new String[] {
                "{\"version\":0}",
                "{\"version\":1,\"autosaveSessionId\":\"00000000-0000-0000-0000-000000000001\"}",
                "{\"version\":1,\"questions\":[{\"question\":\"  \"}]}",
                "{\"version\":1,\"status\":\"completed\",\"questions\":[]}",
                "{\"version\":1,\"visibility\":\"public\",\"status\":\"draft\"}"
        }) {
            assertThatThrownBy(() -> InterviewCommandRequest.update(body(json)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsExistingOccurrenceAndOptionalCreateFields() {
        var command = InterviewCommandRequest.create(body("""
                {"applicationId":"00000000-0000-0000-0000-000000000201",
                 "stageOccurrenceId":"00000000-0000-0000-0000-000000000301",
                 "interviewedOn":"2026-09-19","format":"online",
                 "durationMinutes":60,"interviewerNotes":"  Friendly  ","roundResult":"passed"}
                """));
        assertThat(command.stage()).isNull();
        assertThat(command.stageOccurrenceId()).isNotNull();
        assertThat(command.payload()).containsEntry("durationMinutes", 60)
                .containsEntry("interviewerNotes", "Friendly")
                .containsEntry("roundResult", "passed");
    }

    @Test
    void acceptsPublicationAndAutosaveWithNestedOptionalFields() {
        var command = InterviewCommandRequest.update(body("""
                {"version":3,"autosaveSessionId":"00000000-0000-0000-0000-000000000001",
                 "autosaveRevision":4,"interviewedOn":"2026-09-19","format":null,
                 "durationMinutes":45,"interviewerNotes":"  Note ","roundResult":"passed",
                 "status":"completed","visibility":"public","authorMode":"attributed",
                 "highlights":" Good ","gaps":null,
                 "questions":[{"id":"00000000-0000-0000-0000-000000000002",
                   "category":"project","question":" Design? ","originalAnswer":" Answer ",
                   "followUpNotes":" Follow-up ","improvedAnswer":" Better ","selfRating":4}],
                 "actionItems":[{"id":"00000000-0000-0000-0000-000000000003",
                   "content":"  Prepare  "}]}
                """));
        assertThat(command.payload()).containsEntry("autosaveRevision", 4)
                .containsEntry("visibility", "public")
                .containsEntry("authorMode", "attributed")
                .containsEntry("format", null);
        assertThat(command.payload().toString()).contains("Prepare", "Follow-up", "Better");
    }

    @Test
    void rejectsInvalidCreateAndNestedBounds() {
        String application = "00000000-0000-0000-0000-000000000201";
        String occurrence = "00000000-0000-0000-0000-000000000301";
        for (String json : new String[] {
                "null",
                "{\"applicationId\":\"bad\",\"stage\":\"interview_1\",\"interviewedOn\":\"2026-09-18\"}",
                "{\"applicationId\":\"" + application + "\",\"stage\":\"bad\",\"interviewedOn\":\"2026-09-18\"}",
                "{\"applicationId\":\"" + application + "\",\"stage\":\"interview_1\",\"interviewedOn\":\"bad\"}",
                "{\"applicationId\":\"" + application + "\",\"stage\":\"interview_1\","
                        + "\"interviewedOn\":\"2026-09-18\",\"durationMinutes\":601}",
                "{\"applicationId\":\"" + application + "\",\"stage\":\"interview_1\","
                        + "\"interviewedOn\":\"2026-09-18\",\"format\":\"video\"}",
                "{\"applicationId\":\"" + application + "\",\"stageOccurrenceId\":\"" + occurrence
                        + "\",\"stageOccurredOn\":\"2026-09-18\"}"
        }) {
            assertThatThrownBy(() -> InterviewCommandRequest.create(body(json)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String json : new String[] {
                "{\"version\":1,\"status\":\"invalid\"}",
                "{\"version\":1,\"questions\":{}}",
                "{\"version\":1,\"questions\":[{\"question\":\"Q\",\"category\":\"invalid\"}]}",
                "{\"version\":1,\"questions\":[{\"question\":\"Q\",\"selfRating\":6}]}",
                "{\"version\":1,\"actionItems\":[{\"content\":\"\"}]}",
                "{\"version\":1,\"actionItems\":[{\"content\":\"Task\",\"completed\":1}]}",
                "{\"version\":1,\"autosaveRevision\":1}"
        }) {
            assertThatThrownBy(() -> InterviewCommandRequest.update(body(json)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private tools.jackson.databind.JsonNode body(String json) {
        return mapper.readTree(json);
    }
}
