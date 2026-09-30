package com.jobtrace.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiContractTest {

    @Test
    void foundationContractIsValidOpenApi() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        String contract = Path.of("specs/001-java-migration/contracts/openapi.yaml")
                .toAbsolutePath()
                .toString();

        var result = new OpenAPIV3Parser().readLocation(contract, null, options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        assertThat(result.getOpenAPI().getPaths())
                .containsKeys("/api/health/live", "/api/health/ready");
    }
}

