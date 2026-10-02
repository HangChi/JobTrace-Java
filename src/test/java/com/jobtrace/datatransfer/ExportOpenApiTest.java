package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExportOpenApiTest {

    @Test
    void featureContractContainsOnlyTwoPrivateDownloads() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/007-private-data-export/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getPaths()).containsOnlyKeys(
                "/api/exports/applications", "/api/exports/interviews");
        api.getPaths().values().forEach(path ->
                assertThat(path.readOperationsMap()).containsOnlyKeys(HttpMethod.GET));
        api.getPaths().values().stream().flatMap(path -> path.readOperations().stream())
                .forEach(operation -> {
                    assertThat(operation.getSecurity().getFirst()).containsKey("signedBridge");
                    assertThat(operation.getResponses()).containsKeys("200", "400", "401",
                            "404", "503");
                    assertThat(operation.getResponses().get("200").getContent()).isNotEmpty();
                });
    }

    @Test
    void repositoryContractIncludesOnlyTheTwoNewReadMethods() {
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/001-java-migration/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, new ParseOptions());
        assertThat(result.getMessages()).isEmpty();
        var paths = result.getOpenAPI().getPaths();
        for (String path : new String[] {
                "/api/exports/applications", "/api/exports/interviews"}) {
            assertThat(paths.get(path).readOperationsMap()).containsOnlyKeys(HttpMethod.GET);
            assertThat(paths.get(path).getGet().getSecurity().getFirst())
                    .containsKey("signedIdentityBridge");
        }
    }
}
