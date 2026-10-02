package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApplicationReadOpenApiTest {

    @Test
    void featureContractDefinesOnlyTheProtectedReadSurface() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        String contract = Path.of("specs/004-application-read-model/contracts/openapi.yaml")
                .toAbsolutePath()
                .toString();

        var result = new OpenAPIV3Parser().readLocation(contract, null, options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        assertThat(result.getOpenAPI().getPaths()).containsOnlyKeys(
                "/api/applications", "/api/applications/{id}");
        result.getOpenAPI().getPaths().values().forEach(path ->
                assertThat(path.readOperationsMap()).containsOnlyKeys(HttpMethod.GET));
        result.getOpenAPI().getPaths().values().stream()
                .flatMap(path -> path.readOperations().stream())
                .forEach(operation -> assertThat(operation.getSecurity().getFirst())
                        .containsKey("signedIdentityBridge"));
    }

    @Test
    void featureContractFreezesTheApplicationReadModels() {
        var result = new OpenAPIV3Parser().read(
                Path.of("specs/004-application-read-model/contracts/openapi.yaml")
                        .toAbsolutePath()
                        .toString());

        assertThat(result.getComponents().getSchemas()).containsKeys(
                "ApplicationSummary",
                "ApplicationPage",
                "ApplicationDetail",
                "StageOccurrence",
                "ApplicationEvent",
                "Problem");
        assertThat(result.getComponents().getSchemas().get("ApplicationSummary").getRequired())
                .containsAll(Set.of(
                        "id",
                        "companyName",
                        "positionName",
                        "appliedDate",
                        "type",
                        "status",
                        "latestDate",
                        "stages",
                        "needsFollowUp",
                        "followUpDays",
                        "followUpReason",
                        "version"));
    }
}
