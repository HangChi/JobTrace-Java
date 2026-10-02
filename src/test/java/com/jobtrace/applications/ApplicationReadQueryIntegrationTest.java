package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.application.ApplicationReadQuery;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationReadQueryIntegrationTest extends ApplicationReadDatabaseTest {

    @Autowired
    private ApplicationReadQuery query;

    @Test
    void combinesSearchEnumsStageCityAndInclusiveDateBounds() {
        var criteria = ApplicationListCriteria.from(
                "platform", List.of("submitted"), List.of("campus_recruitment"),
                List.of("assessment"), List.of("上海"), "2026-09-01", "2026-09-01",
                null, null, null, null, null);
        var page = query.list(OWNER, criteria, LocalDate.of(2026, 10, 2));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(FIRST_ID);
        assertThat(query.list(OWNER, ApplicationListCriteria.from(
                null, List.of(), List.of(), List.of(), List.of(),
                "2026-09-02", "2026-09-04", null, null, null, null, null),
                LocalDate.of(2026, 10, 2)).total()).isZero();
    }
}
