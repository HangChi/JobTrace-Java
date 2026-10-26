package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.testing.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayBaselineRehearsalTest extends PostgresIntegrationTest {

    @Test
    void explicitBaselineAdoptsAnExistingSchemaWithoutReplayingLegacyDdl() throws SQLException {
        int originalTables = publicTableCount();
        assertThat(originalTables).isPositive();

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineVersion("20260929000100")
                .baselineDescription("Legacy JobTrace head - isolated rehearsal")
                .baselineOnMigrate(false)
                .load();

        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();

        assertThat(publicTableCount()).isEqualTo(originalTables + 1);
        assertThat(singleValue("select version from flyway_schema_history where type = 'BASELINE'"))
                .isEqualTo("20260929000100");
        assertThat(singleValue("select legacy_head from legacy_schema_marker where id = 1"))
                .isEqualTo("20260929000100_spring_recruitment_type.sql");
    }

    private int publicTableCount() throws SQLException {
        return Integer.parseInt(singleValue(
                "select count(*) from pg_tables where schemaname = 'public'"));
    }

    private String singleValue(String query) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
