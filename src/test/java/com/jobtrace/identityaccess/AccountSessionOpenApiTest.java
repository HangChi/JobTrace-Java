package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountSessionOpenApiTest {

    @Test
    void contractContainsOnlyTheProtectedSessionGet() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/011-session-read-model/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/profile/sessions");
        assertThat(api.getPaths().get("/api/profile/sessions").readOperationsMap())
                .containsOnlyKeys(HttpMethod.GET);
        var operation = api.getPaths().get("/api/profile/sessions").getGet();
        assertThat(operation.getSecurity().getFirst()).containsKey("signedIdentityBridge");
        assertThat(operation.getParameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.getName()).isEqualTo("X-JobTrace-Session-Id");
            assertThat(parameter.getRequired()).isTrue();
        });
        assertThat(api.getComponents().getSchemas()).containsKeys("AccountSession", "Problem");
    }
}
