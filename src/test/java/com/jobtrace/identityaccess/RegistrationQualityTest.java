package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.RegisterAccount;
import com.jobtrace.identityaccess.application.RegistrationEmailCodeHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RegistrationQualityTest extends AccountEmailBindingFixture {

    @Autowired
    private RegisterAccount registration;

    @Autowired
    private RegistrationEmailCodeHasher hasher;

    @BeforeEach
    void addRegistrationColumns() {
        jdbc.execute("alter table users add column username text unique");
        jdbc.execute("alter table users add column display_username text");
        jdbc.execute("alter table users add column role text not null default 'user'");
    }

    @Test
    void contractIncludesExpectedPublicOutcomes() throws Exception {
        String yaml = Files.readString(Path.of(
                "specs/019-registration/contracts/openapi.yaml"));
        assertThat(yaml).contains("/api/auth/register:", "'202':", "'400':",
                "'403':", "'409':", "'429':", "'503':");
    }

    @Test
    void representativeSuccessfulWriteP95StaysUnderOneSecond() {
        var durations = new ArrayList<Long>();
        for (int index = 0; index < 20; index++) {
            String username = "perf_" + index;
            String email = username + "@example.test";
            jdbc.update("""
                    insert into email_verification_codes
                      (id,email,purpose,user_id,code_hash,expires_at)
                    values (gen_random_uuid(),?,'registration',null,?,
                      now()+interval '10 minutes')
                    """, email, hasher.hash(email, "123456"));
            long started = System.nanoTime();
            registration.execute(new RegisterAccount.Input(
                    username, email, "123456", "Password123!", null));
            durations.add((System.nanoTime() - started) / 1_000_000);
        }
        durations.sort(Long::compareTo);
        long p95 = durations.get(18);
        System.out.println("019 registration p95 ms: " + p95);
        assertThat(p95).isLessThanOrEqualTo(1_000);
    }
}
