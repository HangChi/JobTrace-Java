package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.application.ApplicationReadQuery;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationDetailQueryIntegrationTest extends ApplicationReadDatabaseTest {

    @Autowired
    private ApplicationReadQuery query;

    @Test
    void ordersStagesAndEventsAndPreservesNullableFields() {
        var detail = query.findDetail(OWNER, UUID.fromString(FIRST_ID), LocalDate.of(2026, 10, 2))
                .orElseThrow();
        assertThat(detail.stageOccurrences())
                .extracting(occurrence -> occurrence.id())
                .containsExactly(
                        "20000000-0000-4000-8000-000000000001",
                        "20000000-0000-4000-8000-000000000002");
        assertThat(detail.events())
                .extracting(event -> event.id())
                .containsExactly(
                        "30000000-0000-4000-8000-000000000002",
                        "30000000-0000-4000-8000-000000000001");
        assertThat(detail.events()).allSatisfy(event -> assertThat(event.before()).isNull());

        var optional = query.findDetail(OWNER, UUID.fromString(SECOND_ID), LocalDate.of(2026, 10, 2))
                .orElseThrow();
        assertThat(optional.notes()).isNull();
        assertThat(optional.jobUrl()).isNull();
        assertThat(optional.stageOccurrences()).isEmpty();
        assertThat(optional.events()).isEmpty();
        assertThat(optional.stages()).isEmpty();
        assertThat(optional.followUpReason()).isNull();
    }
}
