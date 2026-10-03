package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.ChangeAccountPassword;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountPasswordPerformanceTest extends AccountPasswordDatabaseTest {

    @Autowired
    private ChangeAccountPassword changePassword;

    @Test
    void representativeCredentialSetMeetsOneSecondP95() {
        for (int index = 0; index < 198; index++) {
            String owner = "bulk-password-" + index;
            insertOwner(owner);
            insertCredential("bulk-credential-" + index, owner, CURRENT_HASH);
        }

        String current = CURRENT_PASSWORD;
        long[] elapsed = new long[20];
        for (int index = 0; index < elapsed.length; index++) {
            String replacement = "Pass-%02d-X".formatted(index);
            long started = System.nanoTime();
            changePassword.execute(OWNER, CURRENT_SESSION, current, replacement);
            elapsed[index] = (System.nanoTime() - started) / 1_000_000;
            current = replacement;
        }
        Arrays.sort(elapsed);
        assertThat(elapsed[18]).isLessThanOrEqualTo(1_000);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION);
        System.out.printf("014 password change p95 ms: %d; atomic commits=20%n", elapsed[18]);
    }
}
