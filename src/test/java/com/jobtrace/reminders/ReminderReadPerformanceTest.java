package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.reminders.application.GetReminderPreferences;
import com.jobtrace.reminders.application.GetReminderSummary;
import com.jobtrace.reminders.domain.ReminderSelection;
import com.jobtrace.reminders.infrastructure.PostgresReminderReadQuery;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootTest
class ReminderReadPerformanceTest extends ReminderReadDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void representativeLoadMeetsP95AndFixedQueryCounts() {
        for (int index = 0; index < 250; index++) {
            jdbc.update("""
                    insert into scheduled_reminders(id,owner_id,application_id,title,
                      event_at,notify_at,email_enabled,status,version) values
                    (?::uuid,?,?::uuid,'Synthetic reminder',
                      '2026-10-04T00:00:00Z','2026-10-03T00:00:00Z',false,'pending',1)
                    """, new UUID(0, 10000 + index).toString(), OWNER,
                    "00000000-0000-0000-0000-000000000201");
        }
        var counted = new CountingTemplate(dataSource);
        var query = new PostgresReminderReadQuery(counted);
        var summary = new GetReminderSummary(query, Clock.fixed(READ_INSTANT, ZoneOffset.UTC));
        var preferences = new GetReminderPreferences(query);
        for (int index = 0; index < 10; index++) {
            summary.execute(OWNER, ReminderSelection.ACTIVE);
            preferences.execute(OWNER);
        }
        long[] summaryMs = new long[40];
        long[] preferencesMs = new long[40];
        for (int index = 0; index < 40; index++) {
            counted.reset();
            summaryMs[index] = timed(() -> {
                var result = summary.execute(OWNER, ReminderSelection.ACTIVE);
                assertThat(result.overdue().size() + result.upcoming().size()).isEqualTo(200);
            });
            assertThat(counted.count()).isEqualTo(2);
            counted.reset();
            preferencesMs[index] = timed(() -> preferences.execute(OWNER));
            assertThat(counted.count()).isEqualTo(1);
        }
        Arrays.sort(summaryMs);
        Arrays.sort(preferencesMs);
        assertThat(summaryMs[37]).isLessThanOrEqualTo(500);
        assertThat(preferencesMs[37]).isLessThanOrEqualTo(500);
        System.out.printf("006 read p95 ms: summary=%d preferences=%d; queries=2/1%n",
                summaryMs[37], preferencesMs[37]);
    }

    private static long timed(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return (System.nanoTime() - started) / 1_000_000;
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
