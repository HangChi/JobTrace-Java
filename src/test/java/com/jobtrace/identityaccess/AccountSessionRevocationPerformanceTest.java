package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.infrastructure.PostgresAccountSessionCommand;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootTest
class AccountSessionRevocationPerformanceTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void representativeOwnerSessionsMeetP95WithOneMutation() {
        for (int index = 0; index < 500; index++) {
            insert("revoke-bulk-" + index, OWNER, "2026-09-01T08:00:00Z",
                    "2099-12-31T00:00:00Z", "Synthetic agent");
        }
        var counted = new CountingTemplate(dataSource);
        var command = new PostgresAccountSessionCommand(counted);
        long[] elapsed = new long[40];
        for (int index = 0; index < elapsed.length; index++) {
            counted.reset();
            long started = System.nanoTime();
            assertThat(command.revoke(OWNER, "revoke-bulk-" + index)).isTrue();
            elapsed[index] = (System.nanoTime() - started) / 1_000_000;
            assertThat(counted.count()).isEqualTo(1);
        }
        Arrays.sort(elapsed);
        assertThat(elapsed[37]).isLessThanOrEqualTo(1_000);
        System.out.printf("012 session revoke p95 ms: %d; mutations=1%n", elapsed[37]);
    }

    private static final class CountingTemplate extends NamedParameterJdbcTemplate {
        private int count;

        private CountingTemplate(DataSource dataSource) {
            super(dataSource);
        }

        void reset() {
            count = 0;
        }

        int count() {
            return count;
        }

        @Override
        public <T> java.util.List<T> query(
                String sql, SqlParameterSource parameters, RowMapper<T> mapper) {
            count++;
            return super.query(sql, parameters, mapper);
        }
    }
}
