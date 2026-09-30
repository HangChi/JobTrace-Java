package com.jobtrace.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    @Container
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("jobtrace_test")
                    .withUsername("jobtrace")
                    .withPassword("jobtrace");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.database.url", POSTGRES::getJdbcUrl);
        registry.add("jobtrace.database.username", POSTGRES::getUsername);
        registry.add("jobtrace.database.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }
}
