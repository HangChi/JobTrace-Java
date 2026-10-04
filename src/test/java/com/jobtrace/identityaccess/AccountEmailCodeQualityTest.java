package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.application.RequestAccountEmailCode;
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
class AccountEmailCodeQualityTest extends AccountEmailBindingFixture {

    @Autowired
    private RequestAccountEmailCode requestCode;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @Test
    void contractContainsOnlyProtectedCodePost() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/017-email-binding-code/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/profile/email/code");
        assertThat(api.getPaths().get("/api/profile/email/code").readOperationsMap())
                .containsOnlyKeys(HttpMethod.POST);
        var operation = api.getPaths().get("/api/profile/email/code").getPost();
        assertThat(operation.getSecurity().getFirst()).containsKey("jobTraceBridge");
        assertThat(operation.getRequestBody().getRequired()).isTrue();
    }

    @Test
    void representativeIssuanceMeetsOneSecondP95ExcludingDelivery() {
        var milliseconds = new ArrayList<Long>();
        for (int index = 0; index < 20; index++) {
            String owner = "code-performance-owner-" + index;
            String session = "code-performance-session-" + index;
            insertOwner(owner);
            insertSession(session, owner);
            long started = System.nanoTime();
            requestCode.execute(owner, session, "code-performance-" + index + "@example.test");
            milliseconds.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(milliseconds);
        long p95 = milliseconds.get(18);
        System.out.println("017 email code issuance p95 ms: " + p95);
        assertThat(p95).isLessThanOrEqualTo(1_000);
    }
}
