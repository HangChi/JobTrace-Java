package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JobMarketReadOpenApiTest {

    @Test
    void featureContractParsesAndContainsOnlyTwoProtectedGets() {
        var options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/008-job-market-read-model/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        var paths = result.getOpenAPI().getPaths();
        assertThat(paths).containsOnlyKeys(
                "/api/job-market/campaigns",
                "/api/job-market/campaigns/{campaignId}");
        assertThat(paths.values()).allSatisfy(item -> {
            assertThat(item.getGet()).isNotNull();
            assertThat(item.readOperations()).hasSize(1);
            assertThat(item.getGet().getSecurity()).isNotEmpty();
            assertThat(item.getGet().getResponses().get("200").getHeaders())
                    .containsKey("Cache-Control");
        });
        assertThat(paths.get("/api/job-market/campaigns").getGet().getParameters())
                .extracting(parameter -> parameter.getName())
                .containsExactly("q", "company", "location", "status", "postedFrom",
                        "favorite", "page", "limit");
        assertThat(paths.get("/api/job-market/campaigns/{campaignId}")
                .getGet().getResponses()).containsKeys("400", "401", "404", "503");
    }
}
