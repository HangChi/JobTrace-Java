package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.domain.AccountProfileUpdate;
import com.jobtrace.identityaccess.infrastructure.PostgresAccountProfileCommand;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootTest
class AccountProfilePerformanceTest extends AccountProfileDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void representativeAccountsMeetP95WithOneUpdate() {
        for (int index = 0; index < 498; index++) {
            insert("bulk-profile-" + index, "Bulk", null, null, null);
        }
        var counted = new CountingTemplate(dataSource);
        var command = new PostgresAccountProfileCommand(counted);
        long[] elapsed = new long[40];
        for (int index = 0; index < elapsed.length; index++) {
            counted.reset();
            long started = System.nanoTime();
            assertThat(command.update(new AccountProfileUpdate(
                    OWNER, "Performance " + index, null))).isPresent();
            elapsed[index] = (System.nanoTime() - started) / 1_000_000;
            assertThat(counted.count()).isEqualTo(1);
        }
        Arrays.sort(elapsed);
        assertThat(elapsed[37]).isLessThanOrEqualTo(1_000);
        System.out.printf("013 profile update p95 ms: %d; mutations=1%n", elapsed[37]);
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
