package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.UnbindAccountEmail;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountEmailUnbindingQualityTest extends AccountEmailUnbindingFixture {

    @Autowired
    private UnbindAccountEmail unbindEmail;

    @Test
    void contractContainsOnlyProtectedEmailDelete() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/015-email-unbinding/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/profile/email");
        assertThat(api.getPaths().get("/api/profile/email").readOperationsMap())
                .containsOnlyKeys(HttpMethod.DELETE);
        var operation = api.getPaths().get("/api/profile/email").getDelete();
        assertThat(operation.getSecurity().getFirst()).containsKey("jobTraceBridge");
        assertThat(operation.getRequestBody().getRequired()).isTrue();
    }

    @Test
    void representativeWritesMeetOneSecondP95() {
        var milliseconds = new ArrayList<Long>();
        for (int index = 0; index < 20; index++) {
            String owner = "performance-owner-" + index;
            String session = "performance-session-" + index;
            insertOwner(owner);
            insertCredential("credential-" + owner, owner, CURRENT_HASH);
            insertSession(session, owner);
            jdbc.update("update users set recovery_email=?,email_verified=true where id=?",
                    owner + "@example.test", owner);
            long started = System.nanoTime();
            unbindEmail.execute(owner, session, CURRENT_PASSWORD);
            milliseconds.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(milliseconds);
        long p95 = milliseconds.get(18);
        System.out.println("015 email unbinding p95 ms: " + p95);
        assertThat(p95).isLessThanOrEqualTo(1_000);
    }
}
