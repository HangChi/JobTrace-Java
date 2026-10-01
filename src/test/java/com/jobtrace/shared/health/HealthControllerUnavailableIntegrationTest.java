package com.jobtrace.shared.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
    "jobtrace.database.url=jdbc:postgresql://127.0.0.1:1/jobtrace",
    "spring.datasource.hikari.initialization-fail-timeout=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthControllerUnavailableIntegrationTest {

    private static final Duration READINESS_FAILURE_BUDGET = Duration.ofSeconds(10);

    @Autowired
    private HealthController controller;

    @Autowired
    private HikariDataSource dataSource;

    @Test
    void reportsAnUnavailableDatabaseWithinTheReadinessBudget() {
        assertThat(dataSource.getConnectionTimeout()).isLessThanOrEqualTo(5_000L);
        assertThat(dataSource.getDataSourceProperties())
                .containsEntry("connectTimeout", "5")
                .containsEntry("socketTimeout", "5");

        long startedAt = System.nanoTime();
        var response = controller.ready();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "error");
        assertThat(elapsed).isLessThan(READINESS_FAILURE_BUDGET);
    }
}
