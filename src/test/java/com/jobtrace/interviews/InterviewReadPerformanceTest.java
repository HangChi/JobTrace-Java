package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applicationdialog.application.GetApplicationDialogData;
import com.jobtrace.applications.application.GetApplicationDetail;
import com.jobtrace.applications.infrastructure.PostgresApplicationReadQuery;
import com.jobtrace.interviews.application.ListInterviewsForApplication;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import com.jobtrace.interviews.infrastructure.PostgresInterviewReadQuery;
import java.time.Clock;
import java.time.Instant;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class InterviewReadPerformanceTest extends InterviewReadDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void fixedRepresentativeLoadMeetsP95AndFixedQueryCounts() {
        for (int index = 0; index < 100; index++) {
            String id = new UUID(0, 10000 + index).toString();
            jdbc.update("""
                    insert into interview_reviews (id,owner_id,application_id,
                      stage_snapshot,interviewed_on,round_result,status,visibility,
                      author_mode,version,created_at,updated_at) values
                    (?::uuid,?,?::uuid,'interview_1','2026-09-19','pending',
                      'draft','private','anonymous',1,now(),now())
                    """, id, OWNER, APPLICATION_ID);
        }
        var counted = new CountingTemplate(dataSource);
        var interviews = new PostgresInterviewReadQuery(counted, mapper);
        var applications = new PostgresApplicationReadQuery(counted, mapper);
        var detail = new GetApplicationDetail(applications,
                Clock.fixed(Instant.parse("2026-10-02T01:00:00Z"), ZoneOffset.UTC));
        var dialog = new GetApplicationDialogData(detail,
                new ListInterviewsForApplication(interviews));
        var criteria = InterviewListCriteria.from(null, null, null, null, null,
                null, null, null, null, "50");
        UUID reviewId = UUID.fromString(R102);
        UUID applicationId = UUID.fromString(APPLICATION_ID);

        for (int index = 0; index < 10; index++) {
            interviews.list(OWNER, criteria);
            interviews.findDetail(OWNER, reviewId);
            dialog.execute(OWNER, applicationId);
        }
        long[] listMs = new long[40];
        long[] detailMs = new long[40];
        long[] dialogMs = new long[40];
        for (int index = 0; index < 40; index++) {
            counted.reset();
            listMs[index] = timed(() -> interviews.list(OWNER, criteria));
            assertThat(counted.count()).isEqualTo(2);
            counted.reset();
            detailMs[index] = timed(() -> interviews.findDetail(OWNER, reviewId));
            assertThat(counted.count()).isEqualTo(3);
            counted.reset();
            dialogMs[index] = timed(() -> dialog.execute(OWNER, applicationId));
            assertThat(counted.count()).isEqualTo(4);
        }
        for (long[] samples : new long[][] {listMs, detailMs, dialogMs}) {
            Arrays.sort(samples);
            assertThat(samples[37]).isLessThanOrEqualTo(500);
        }
        System.out.printf("005 read p95 ms: list=%d detail=%d dialog=%d; queries=2/3/4%n",
                listMs[37], detailMs[37], dialogMs[37]);
    }

    private static long timed(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static final class CountingTemplate extends NamedParameterJdbcTemplate {
        private int count;

        private CountingTemplate(DataSource dataSource) {
            super(dataSource);
        }

        void reset() { count = 0; }
        int count() { return count; }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource parameters, Class<T> type) {
            count++;
            return super.queryForObject(sql, parameters, type);
        }

        @Override
        public <T> java.util.List<T> query(
                String sql, SqlParameterSource parameters, RowMapper<T> mapper) {
            count++;
            return super.query(sql, parameters, mapper);
        }
    }
}
