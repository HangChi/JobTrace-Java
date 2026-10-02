package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.application.ApplicationReadQuery;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationOwnerIsolationIntegrationTest extends ApplicationReadDatabaseTest {

    @Autowired
    private ApplicationReadQuery query;

    @Test
    void listCountAndDetailNeverIncludeOtherOwner() {
        var criteria = ApplicationListCriteria.from(
                null, List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null);
        var page = query.list(OWNER, criteria, LocalDate.of(2026, 10, 2));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(item -> item.id())
                .containsExactly(FIRST_ID, SECOND_ID);
        assertThat(query.findDetail(OWNER, UUID.fromString(OTHER_ID), LocalDate.of(2026, 10, 2)))
                .isEmpty();
    }
}
