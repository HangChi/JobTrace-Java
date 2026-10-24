package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.BindAccountEmail;
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
class AccountEmailBindingQualityTest extends AccountEmailBindingFixture {

    @Autowired
    private BindAccountEmail bindEmail;

    @Test
    void contractContainsOnlyProtectedEmailPatch() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        var result = new OpenAPIV3Parser().readLocation(Path.of(
                "specs/016-email-binding/contracts/openapi.yaml")
                .toAbsolutePath().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        var api = result.getOpenAPI();
        assertThat(api.getPaths()).containsOnlyKeys("/api/profile/email");
        assertThat(api.getPaths().get("/api/profile/email").readOperationsMap())
                .containsOnlyKeys(HttpMethod.PATCH);
        var operation = api.getPaths().get("/api/profile/email").getPatch();
        assertThat(operation.getSecurity().getFirst()).containsKey("jobTraceBridge");
        assertThat(operation.getRequestBody().getRequired()).isTrue();
    }

    @Test
    void representativeWritesMeetOneSecondP95() {
        var milliseconds = new ArrayList<Long>();
        for (int index = 0; index < 20; index++) {
            String owner = "binding-performance-owner-" + index;
            String session = "binding-performance-session-" + index;
            String email = "binding-performance-" + index + "@example.test";
            String codeId = "10000000-0000-0000-0000-" + String.format("%012d", index);
            insertOwner(owner);
            insertCredential("credential-" + owner, owner, CURRENT_HASH);
            insertSession(session, owner);
            insertCode(codeId, owner, email);
            jdbc.update("update email_verification_codes set code_hash=? where id=?::uuid",
                    codeHasher.hash(owner, email, CORRECT_CODE), codeId);
            long started = System.nanoTime();
            bindEmail.execute(owner, session, CURRENT_PASSWORD, email, CORRECT_CODE);
            milliseconds.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(milliseconds);
        long p95 = milliseconds.get(18);
        System.out.println("016 email binding p95 ms: " + p95);
        assertThat(p95).isLessThanOrEqualTo(1_000);
    }
}
