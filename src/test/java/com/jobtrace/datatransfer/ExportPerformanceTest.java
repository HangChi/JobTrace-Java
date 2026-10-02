package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.datatransfer.application.ExportApplications;
import com.jobtrace.datatransfer.application.ExportInterviews;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExportPerformanceTest extends ExportDatabaseTest {

    @Autowired private ExportApplications applications;
    @Autowired private ExportInterviews interviews;

    @Test
    void hundredSelectedDownloadsMeetLocalP95BudgetIncludingSerialization() {
        var applicationIds = new ArrayList<String>();
        var reviewIds = new ArrayList<String>();
        applicationIds.add(A101);
        reviewIds.add(R301);
        for (int index = 1; index < 100; index++) {
            String applicationId = UUID.nameUUIDFromBytes(("export-app-" + index)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            String reviewId = UUID.nameUUIDFromBytes(("export-review-" + index)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            applicationIds.add(applicationId);
            reviewIds.add(reviewId);
            jdbc.update("""
                    insert into applications(id,owner_id,company_name,position_name,applied_date,
                      type,status,latest_date,version,created_at,updated_at)
                    values (?::uuid, ?, '合成公司', '合成岗位', '2026-09-01',
                      'spring_recruitment','submitted','2026-09-01',1,now(),now())
                    """, applicationId, OWNER);
            jdbc.update("""
                    insert into interview_reviews(id,owner_id,application_id,stage_snapshot,
                      interviewed_on,round_result,status,visibility,author_mode,version,
                      created_at,updated_at)
                    values (?::uuid, ?, ?::uuid, 'interview_1', '2026-09-01',
                      'passed','completed','private','anonymous',1,now(),now())
                    """, reviewId, OWNER, applicationId);
            jdbc.update("""
                    insert into interview_questions(id,interview_review_id,sort_order,category,question)
                    values (?::uuid, ?::uuid, 0, 'other', '合成问题')
                    """, UUID.nameUUIDFromBytes(("export-question-" + index)
                            .getBytes(StandardCharsets.UTF_8)), reviewId);
        }
        var appSelection = ApplicationExportSelection.from("selected", "xlsx", applicationIds,
                null, List.of(), List.of(), List.of(), List.of(), null, null);
        var reviewSelection = InterviewExportSelection.from(reviewIds);
        var appTimes = new ArrayList<Long>();
        var reviewTimes = new ArrayList<Long>();
        for (int sample = 0; sample < 50; sample++) {
            long appStarted = System.nanoTime();
            assertThat(applications.execute(OWNER, appSelection).content()).isNotEmpty();
            long appElapsed = System.nanoTime() - appStarted;
            long reviewStarted = System.nanoTime();
            assertThat(interviews.execute(OWNER, reviewSelection).content()).isNotEmpty();
            long reviewElapsed = System.nanoTime() - reviewStarted;
            if (sample >= 10) {
                appTimes.add(appElapsed);
                reviewTimes.add(reviewElapsed);
            }
        }
        assertThat(p95Millis(appTimes)).isLessThanOrEqualTo(500);
        assertThat(p95Millis(reviewTimes)).isLessThanOrEqualTo(500);
    }

    private static double p95Millis(List<Long> times) {
        Collections.sort(times);
        return times.get((int) Math.ceil(times.size() * 0.95) - 1) / 1_000_000d;
    }
}
