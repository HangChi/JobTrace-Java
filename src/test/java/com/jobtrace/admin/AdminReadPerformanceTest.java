package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.infrastructure.PostgresAdminReadQuery;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import tools.jackson.databind.ObjectMapper;

class AdminReadPerformanceTest extends AdminReadQueryIntegrationTest {

    @Autowired
    private DataSource performanceDataSource;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void thousandUserDatasetMeetsBudgetAndFixedQueryCounts() {
        jdbc.update("""
                insert into users(id,username,email,role,disabled,access_version,created_at)
                select 'perf-' || n,'user-' || n,'user-' || n || '@example.test','user',false,1,
                  now()-(n || ' minutes')::interval from generate_series(1,1000) n
                """);
        jdbc.execute("analyze users; analyze sessions; analyze applications; "
                + "analyze interview_reviews; analyze admin_audit_events");
        var counted = new CountingTemplate(performanceDataSource);
        var adapter = new PostgresAdminReadQuery(counted, mapper);
        var criteria = new AdminQuery.Users(null, AdminQuery.UserRole.ALL,
                AdminQuery.UserStatus.ALL, null, null, 1, 50);
        for (int index = 0; index < 10; index++) {
            serialize(adapter.users(criteria));
        }
        long[] samples = new long[40];
        for (int index = 0; index < samples.length; index++) {
            counted.reset();
            long started = System.nanoTime();
            serialize(adapter.users(criteria));
            samples[index] = (System.nanoTime() - started) / 1_000_000;
            assertThat(counted.count()).isEqualTo(2);
        }
        Arrays.sort(samples);
        assertThat(samples[37]).isLessThanOrEqualTo(500);
        System.out.printf("009 admin user list p95 ms: %d; queries=2%n", samples[37]);
    }

    private void serialize(Object value) {
        mapper.writeValueAsBytes(value);
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

        @Override
        public <T> T queryForObject(
                String sql, SqlParameterSource parameters, Class<T> requiredType) {
            count++;
            return super.queryForObject(sql, parameters, requiredType);
        }
    }
}
