package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.interviews.application.InterviewReadQuery;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InterviewListQueryIntegrationTest extends InterviewReadDatabaseTest {

    @Autowired
    private InterviewReadQuery query;

    @Test
    void combinesApplicationEnumsAndInclusiveDateBounds() {
        var criteria = InterviewListCriteria.from(APPLICATION_ID, null,
                List.of("completed"), List.of("assessment"), List.of("passed"),
                List.of("anonymous"), "2026-09-20", "2026-09-20", null, null);
        var page = query.list(OWNER, criteria);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(R102);
        assertThat(query.list(OWNER, InterviewListCriteria.from(null, null,
                null, null, null, null, "2026-09-21", null, null, null)).total()).isZero();
    }

    @Test
    void assessmentAndLinkedStageUseStoredOrOccurrenceStage() {
        var page = query.list(OWNER, InterviewListCriteria.from(APPLICATION_ID, null,
                null, null, null, null, null, null, null, null));
        assertThat(page.items()).extracting(item -> item.stage().value())
                .containsExactly("assessment", "interview_1");
        assertThat(page.items()).extracting(item -> item.linked())
                .containsExactly(false, true);
        jdbc.update("delete from application_stage_occurrences where id = ?::uuid",
                "00000000-0000-0000-0000-000000000301");
        var unlinked = query.list(OWNER, InterviewListCriteria.from(APPLICATION_ID, null,
                null, List.of("interview_1"), null, null, null, null, null, null));
        assertThat(unlinked.items()).extracting(item -> item.id()).containsExactly(R101);
        assertThat(unlinked.items().getFirst().stageOccurrenceId()).isNull();
    }
}
