package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminReadOpenApiTest {

    @Test
    void featureContractParsesAndContainsOnlyFourProtectedGets() {
        var options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/009-admin-read-model/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        var paths = result.getOpenAPI().getPaths();
        assertThat(paths).containsOnlyKeys("/api/admin/summary", "/api/admin/users",
                "/api/admin/users/{id}", "/api/admin/audit-events");
        assertThat(paths.values()).allSatisfy(item -> {
            assertThat(item.getGet()).isNotNull();
            assertThat(item.readOperations()).hasSize(1);
            assertThat(item.getGet().getSecurity()).isNotEmpty();
            assertThat(item.getGet().getResponses().get("200").getHeaders())
                    .containsKey("Cache-Control");
        });
        assertThat(paths.get("/api/admin/users").getGet().getParameters())
                .extracting(parameter -> parameter.getName())
                .containsExactly("q", "role", "status", "registeredFrom", "registeredTo",
                        "page", "limit");
    }

    @Test
    void repositoryContractPublishesTheSameFourProtectedReads() {
        var options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/001-java-migration/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var paths = result.getOpenAPI().getPaths();
        for (String path : new String[] {"/api/admin/summary", "/api/admin/users",
                "/api/admin/users/{id}", "/api/admin/audit-events"}) {
            assertThat(paths).containsKey(path);
            assertThat(paths.get(path).getGet()).isNotNull();
            assertThat(paths.get(path).getGet().getSecurity()).isNotEmpty();
            assertThat(paths.get(path).getGet().getResponses().get("200").getHeaders())
                    .containsKey("Cache-Control");
        }
        assertThat(paths.get("/api/admin/users/{id}").getPatch()).isNotNull();
        assertThat(paths.keySet()).noneMatch(path -> path.startsWith("/api/admin/")
                && !path.equals("/api/admin/summary")
                && !path.equals("/api/admin/users")
                && !path.equals("/api/admin/users/{id}")
                && !path.equals("/api/admin/audit-events"));
    }
}
