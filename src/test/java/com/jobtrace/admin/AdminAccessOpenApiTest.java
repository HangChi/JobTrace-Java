package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminAccessOpenApiTest {

    @Test
    void featureContractContainsOnlyTheAccessPatch() {
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/010-admin-access-change/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, new ParseOptions());

        assertThat(result.getMessages()).isEmpty();
        var paths = result.getOpenAPI().getPaths();
        assertThat(paths).containsOnlyKeys("/api/admin/users/{id}");
        assertThat(paths.get("/api/admin/users/{id}").getPatch()).isNotNull();
        assertThat(paths.get("/api/admin/users/{id}").getPatch().getSecurity()).isNotEmpty();
        assertThat(paths.get("/api/admin/users/{id}").readOperations()).hasSize(1);
        assertThat(paths.get("/api/admin/users/{id}").getPatch().getResponses())
                .containsKeys("200", "400", "401", "403", "404", "409", "503");
    }
}
