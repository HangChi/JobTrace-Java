package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountSessionRevocationOpenApiTest {

    @Test
    void contractContainsOnlyTheProtectedSessionDelete() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/012-session-revocation/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/profile/sessions");
        assertThat(api.getPaths().get("/api/profile/sessions").readOperationsMap())
                .containsOnlyKeys(HttpMethod.DELETE);
        var operation = api.getPaths().get("/api/profile/sessions").getDelete();
        assertThat(operation.getSecurity().getFirst()).containsKey("signedIdentityBridge");
        assertThat(operation.getRequestBody().getRequired()).isTrue();
        assertThat(api.getComponents().getSchemas())
                .containsKeys("SessionRevocationResult", "Problem");
    }
}
