package com.jobtrace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jobtrace.database.url=jdbc:postgresql://127.0.0.1:1/jobtrace",
        "jobtrace.database.username=jobtrace",
        "jobtrace.database.password=test-only",
        "spring.flyway.enabled=false"
})
class JobTraceApplicationTest {

    @Test
    void contextLoadsWithFlywayDisabled() {
    }
}

