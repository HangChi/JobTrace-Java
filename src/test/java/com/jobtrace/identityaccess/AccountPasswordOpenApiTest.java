package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountPasswordOpenApiTest {

    @Test
    void contractContainsOnlyProtectedPasswordPost() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/014-password-change/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/profile/password");
        assertThat(api.getPaths().get("/api/profile/password").readOperationsMap())
                .containsOnlyKeys(HttpMethod.POST);
        var operation = api.getPaths().get("/api/profile/password").getPost();
        assertThat(operation.getSecurity().getFirst()).containsKey("jobTraceBridge");
        assertThat(operation.getRequestBody().getRequired()).isTrue();
        assertThat(operation.getParameters()).singleElement()
                .satisfies(parameter -> assertThat(parameter.getName())
                        .isEqualTo("X-JobTrace-Session-Id"));
    }
}
