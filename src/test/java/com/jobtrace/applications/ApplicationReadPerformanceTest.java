package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.application.ApplicationReadQuery;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationReadPerformanceTest extends ApplicationReadDatabaseTest {

    @Autowired
    private ApplicationReadQuery query;

    @Test
    void representativeListAndDetailReadsMeetFiveHundredMillisecondP95() {
        for (int index = 0; index < 100; index++) {
            jdbc.update("""
                    insert into applications (
                      id, owner_id, company_name, position_name, applied_date,
                      type, status, latest_date, version, created_at, updated_at
                    ) values (?::uuid, ?, ?, 'Engineer', '2026-09-01',
                              'campus_recruitment', 'submitted', '2026-09-10',
                              1, now(), now())
                    """, new UUID(0, 1000 + index).toString(), OWNER, "Company " + index);
        }
        var criteria = ApplicationListCriteria.from(
                null, List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, "50");
        LocalDate today = LocalDate.of(2026, 10, 2);
        UUID id = UUID.fromString(FIRST_ID);
        for (int warmup = 0; warmup < 10; warmup++) {
            query.list(OWNER, criteria, today);
            query.findDetail(OWNER, id, today);
        }
        long[] listMs = new long[40];
        long[] detailMs = new long[40];
        for (int sample = 0; sample < 40; sample++) {
            long start = System.nanoTime();
            query.list(OWNER, criteria, today);
            listMs[sample] = (System.nanoTime() - start) / 1_000_000;
            start = System.nanoTime();
            query.findDetail(OWNER, id, today);
            detailMs[sample] = (System.nanoTime() - start) / 1_000_000;
        }
        Arrays.sort(listMs);
        Arrays.sort(detailMs);
        assertThat(listMs[37]).isLessThanOrEqualTo(500);
        assertThat(detailMs[37]).isLessThanOrEqualTo(500);
    }
}
