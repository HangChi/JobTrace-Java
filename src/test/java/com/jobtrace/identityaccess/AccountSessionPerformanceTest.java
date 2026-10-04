package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.infrastructure.PostgresAccountSessionReadQuery;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootTest
class AccountSessionPerformanceTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void fiveHundredActiveSessionsMeetP95WithOneRead() {
        for (int index = 0; index < 497; index++) {
            insert("bulk-" + index, OWNER, "2026-09-01T08:00:00Z",
                    "2099-12-31T00:00:00Z", "Synthetic agent");
        }
        var counted = new CountingTemplate(dataSource);
        var query = new PostgresAccountSessionReadQuery(counted);
        for (int index = 0; index < 10; index++) {
            query.listActive(OWNER, CURRENT);
        }
        long[] elapsed = new long[40];
        for (int index = 0; index < elapsed.length; index++) {
            counted.reset();
            long started = System.nanoTime();
            assertThat(query.listActive(OWNER, CURRENT)).hasSize(500);
            elapsed[index] = (System.nanoTime() - started) / 1_000_000;
            assertThat(counted.count()).isEqualTo(1);
        }
        Arrays.sort(elapsed);
        assertThat(elapsed[37]).isLessThanOrEqualTo(500);
        System.out.printf("011 session read p95 ms: %d; queries=1%n", elapsed[37]);
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
