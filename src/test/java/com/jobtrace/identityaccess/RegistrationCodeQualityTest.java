package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.application.RequestRegistrationEmailCode;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RegistrationCodeQualityTest extends AccountEmailBindingFixture {

    @Autowired
    private RequestRegistrationEmailCode requestCode;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @Test
    void contractContainsOnlyAnonymousRegistrationCodePost() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/018-registration-email-code/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/auth/email-code");
        assertThat(api.getPaths().get("/api/auth/email-code").readOperationsMap())
                .containsOnlyKeys(HttpMethod.POST);
        assertThat(api.getPaths().get("/api/auth/email-code").getPost().getSecurity())
                .isNullOrEmpty();
    }

    @Test
    void representativeIssuanceMeetsOneSecondP95ExcludingDelivery() {
        var milliseconds = new ArrayList<Long>();
        for (int index = 0; index < 20; index++) {
            long started = System.nanoTime();
            requestCode.execute("registration-performance-" + index + "@example.test");
            milliseconds.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(milliseconds);
        long p95 = milliseconds.get(18);
        System.out.println("018 registration code issuance p95 ms: " + p95);
        assertThat(p95).isLessThanOrEqualTo(1_000);
    }
}
