package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReminderReadOpenApiTest {

    @Test
    void contractContainsOnlyTwoProtectedGets() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/006-reminder-read-model/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getPaths()).containsOnlyKeys(
                "/api/reminders", "/api/reminder-settings");
        api.getPaths().values().forEach(path ->
                assertThat(path.readOperationsMap()).containsOnlyKeys(HttpMethod.GET));
        api.getPaths().values().stream().flatMap(path -> path.readOperations().stream())
                .forEach(operation -> assertThat(operation.getSecurity().getFirst())
                        .containsKey("signedIdentityBridge"));
        assertThat(api.getComponents().getSchemas()).containsKeys(
                "Reminder", "ReminderSummary", "ReminderEmailAvailability",
                "ReminderPreferences", "Problem");
    }
}
