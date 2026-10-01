package com.jobtrace.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.nio.file.Files;
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
        var analytics = result.getOpenAPI().getPaths()
                .get("/api/analytics/summary")
                .getGet();
        assertThat(analytics.getSecurity().getFirst())
                .containsKey("signedIdentityBridge");
        var scheme = result.getOpenAPI().getComponents()
                .getSecuritySchemes()
                .get("signedIdentityBridge");
        assertThat(scheme.getName()).isEqualTo("Authorization");
        assertThat(scheme.getIn().toString()).isEqualTo("header");
    }

    @Test
    void signedIdentityBridgeContractFreezesEveryMandatoryV1Field() throws Exception {
        String contract = Files.readString(Path.of(
                "specs/003-signed-identity-bridge/contracts/identity-bridge.md"));

        assertThat(contract)
                .contains("GET /api/analytics/summary")
                .contains("Authorization: JobTraceBridge <compact-JWS>")
                .contains("x-request-id: <UUID>")
                .contains("\"alg\":\"HS256\"")
                .contains("\"kid\"")
                .contains("\"typ\":\"JWT\"");
        assertThat(contract).contains(
                "\"ver\"",
                "\"iss\"",
                "\"aud\"",
                "\"sub\"",
                "\"role\"",
                "\"av\"",
                "\"rid\"",
                "\"mth\"",
                "\"pth\"",
                "\"iat\"",
                "\"nbf\"",
                "\"exp\"",
                "\"jti\"");
    }
}
