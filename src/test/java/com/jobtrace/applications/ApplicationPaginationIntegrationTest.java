package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.application.ApplicationReadQuery;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationPaginationIntegrationTest extends ApplicationReadDatabaseTest {

    @Autowired
    private ApplicationReadQuery query;

    @Test
    void traversesEverySortAndDirectionWithoutDuplicates() {
        jdbc.update("""
                insert into applications (
                  id, owner_id, company_name, position_name, applied_date,
                  type, status, latest_date, version, created_at, updated_at
                ) values
                ('10000000-0000-4000-8000-000000000004', ?, 'Contract Company',
                 'Platform Engineer', '2026-09-01', 'campus_recruitment',
                 'submitted', '2026-09-10', 1, now(), now())
                """, OWNER);
        LocalDate today = LocalDate.of(2026, 10, 2);
        for (String sort : List.of("company", "position", "appliedDate", "latestDate")) {
            for (String direction : List.of("asc", "desc")) {
                List<String> ids = new ArrayList<>();
                String cursor = null;
                do {
                    var criteria = criteria(sort, direction, cursor, "1", "1");
                    var page = query.list(OWNER, criteria, today);
                    assertThat(page.total()).isEqualTo(3);
                    ids.addAll(page.items().stream().map(item -> item.id()).toList());
                    cursor = page.nextCursor();
                } while (cursor != null);
                assertThat(ids).hasSize(3);
                assertThat(new HashSet<>(ids)).hasSize(3);
            }
        }
    }

    @Test
    void defaultOrderPrioritizesSubmittedAndOffsetPageKeepsTotal() {
        var first = query.list(OWNER, criteria(null, null, null, "1", "1"),
                LocalDate.of(2026, 10, 2));
        var second = query.list(OWNER, criteria(null, null, null, "2", "1"),
                LocalDate.of(2026, 10, 2));
        var beyond = query.list(OWNER, criteria(null, null, null, "99", "1"),
                LocalDate.of(2026, 10, 2));

        assertThat(first.items()).extracting(item -> item.id()).containsExactly(FIRST_ID);
        assertThat(second.items()).extracting(item -> item.id()).containsExactly(SECOND_ID);
        assertThat(beyond.total()).isEqualTo(2);
        assertThat(beyond.items()).isEmpty();
    }

    private static ApplicationListCriteria criteria(
            String sort, String direction, String cursor, String page, String limit) {
        return ApplicationListCriteria.from(
                null, List.of(), List.of(), List.of(), List.of(),
                null, null, sort, direction, cursor, page, limit);
    }
}
