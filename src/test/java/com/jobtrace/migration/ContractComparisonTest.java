package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class ContractComparisonTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void capturedFixturesContainTheCompleteLegacyContract() throws IOException {
        for (String fixture : new String[] {"empty.legacy.json", "representative.legacy.json"}) {
            JsonNode response = loadFixture(fixture);

            assertThat(response.fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "total",
                    "submitted",
                    "refused",
                    "offers",
                    "addedThisWeek",
                    "stageDistribution",
                    "followUps",
                    "progressReminders");
            assertThat(response.path("followUps").isArray()).isTrue();
            assertThat(response.path("progressReminders").isArray()).isTrue();
        }
    }

    @Test
    void comparisonIgnoresObjectFieldOrderButPreservesValuesAndArrayOrder() throws IOException {
        JsonNode legacy = OBJECT_MAPPER.readTree("{\"total\":1,\"items\":[\"first\",\"second\"]}");
        JsonNode reordered = OBJECT_MAPPER.readTree("{\"items\":[\"first\",\"second\"],\"total\":1}");
        JsonNode divergent = OBJECT_MAPPER.readTree("{\"items\":[\"second\",\"first\"],\"total\":1}");

        assertEquivalent(legacy, reordered);
        assertThatThrownBy(() -> assertEquivalent(legacy, divergent))
                .isInstanceOf(AssertionError.class);
    }

    public static JsonNode loadFixture(String name) throws IOException {
        String path = "contracts/analytics-summary/" + name;
        try (InputStream input = ContractComparisonTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing contract fixture: " + path);
            }
            return OBJECT_MAPPER.readTree(input);
        }
    }

    public static void assertEquivalent(JsonNode legacy, JsonNode target) {
        assertThat(normalize(target)).isEqualTo(normalize(legacy));
    }

    private static JsonNode normalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            value.properties().forEach(entry -> fields.put(entry.getKey(), normalize(entry.getValue())));
            fields.forEach(normalized::set);
            return normalized;
        }
        if (value.isArray()) {
            ArrayNode normalized = OBJECT_MAPPER.createArrayNode();
            value.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        return value;
    }
}
