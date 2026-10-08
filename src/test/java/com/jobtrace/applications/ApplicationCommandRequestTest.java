package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.applications.web.ApplicationCommandRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ApplicationCommandRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsWithLegacyDefaultsAndTrimsFields() {
        var request = ApplicationCommandRequest.create(body("""
                {"companyName":"  Acme ","positionName":" Engineer ",
                 "appliedDate":"2026-10-01","stages":[]}
                """));
        assertThat(request.companyName()).isEqualTo("Acme");
        assertThat(request.positionName()).isEqualTo("Engineer");
        assertThat(request.payload()).containsEntry("type", "campus_recruitment")
                .containsEntry("status", "submitted");
        assertThat(request.stages()).isEmpty();
    }

    @Test
    void rejectsInvalidInputsBeforeAnyWrite() {
        for (String json : new String[] {
                "{\"companyName\":\"\",\"positionName\":\"Engineer\",\"appliedDate\":\"2026-10-01\"}",
                "{\"companyName\":\"A\",\"positionName\":\"B\",\"appliedDate\":\"bad\"}",
                "{\"companyName\":\"A\",\"positionName\":\"B\",\"appliedDate\":\"2026-10-01\",\"status\":\"invalid\"}",
                "{\"companyName\":\"A\",\"positionName\":\"B\","
                        + "\"appliedDate\":\"2026-10-01\",\"jobUrl\":\"file:///x\"}",
                "{\"companyName\":\"A\",\"positionName\":\"B\","
                        + "\"appliedDate\":\"2026-10-01\",\"stages\":[{\"stage\":\"bad\","
                        + "\"occurredOn\":\"2026-10-01\"}]}"
        }) {
            assertThatThrownBy(() -> ApplicationCommandRequest.create(body(json)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void updateRequiresVersionAndConsistentChangeDate() {
        assertThatThrownBy(() -> ApplicationCommandRequest.update(body("""
                {"companyName":"A","positionName":"B","appliedDate":"2026-10-01",
                 "version":1,"changeDate":"2026-09-30"}
                """))).isInstanceOf(IllegalArgumentException.class);
        var request = ApplicationCommandRequest.update(body("""
                {"companyName":"A","positionName":"B","appliedDate":"2026-10-01",
                 "version":3,"changeDate":"2026-10-02"}
                """));
        assertThat(request.version()).isEqualTo(3);
    }

    @Test
    void bulkSelectionDeduplicatesAndBoundsIds() {
        String id = "10000000-0000-4000-8000-000000000001";
        assertThat(ApplicationCommandRequest.ids(body("{\"ids\":[\"" + id + "\",\"" + id + "\"]}")))
                .hasSize(1);
        assertThatThrownBy(() -> ApplicationCommandRequest.ids(body("{\"ids\":[]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesExplicitNullableFieldsAndAcceptsLinkedPostAndStage() {
        var request = ApplicationCommandRequest.create(body("""
                {"companyName":"A","positionName":"B","appliedDate":"2026-10-01",
                 "type":"social_recruitment","status":"offer","city":null,
                 "jobUrl":"https://example.test/apply","notes":"",
                 "jobMarketPostId":"10000000-0000-4000-8000-000000000001",
                 "stages":[{"stage":"screening","occurredOn":"2026-10-01"}]}
                """));
        assertThat(request.payload()).containsEntry("city", null)
                .containsEntry("status", "offer")
                .containsEntry("type", "social_recruitment");
        assertThat(request.jobMarketPostId()).isNotNull();
        assertThat(request.stages()).hasSize(1);
        assertThatThrownBy(() -> ApplicationCommandRequest.create(body("""
                {"companyName":"A","positionName":"B","appliedDate":"2026-10-01",
                 "jobMarketPostId":null}
                """))).isInstanceOf(IllegalArgumentException.class);
    }

    private tools.jackson.databind.JsonNode body(String json) {
        return mapper.readTree(json);
    }
}
