package com.jobtrace.shared.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest
class HealthControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private HealthController controller;

    @Test
    void readinessUsesARealPostgresConnection() {
        var response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
    }
}

