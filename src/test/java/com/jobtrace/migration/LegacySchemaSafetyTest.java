package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.shared.config.JobTraceProperties;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class LegacySchemaSafetyTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobTraceProperties properties;

    @Test
    void startupLeavesTheLegacySchemaUntouchedAndDoesNotClaimFlywayHistory() {
        assertThat(properties.migration().flywayEnabled()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select legacy_head from legacy_schema_marker where id = 1",
                String.class))
                .isEqualTo("20260929000100_spring_recruitment_type.sql");
        assertThat(jdbcTemplate.queryForObject(
                "select to_regclass('public.flyway_schema_history')::text",
                String.class))
                .isNull();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
        assertThat(Files.exists(Path.of("src/test/resources/postgres/interviews-read-model.sql")))
                .isTrue();
    }
}
