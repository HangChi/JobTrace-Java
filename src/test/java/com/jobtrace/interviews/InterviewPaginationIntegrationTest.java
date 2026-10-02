package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.application.InterviewReadQuery;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InterviewPaginationIntegrationTest extends InterviewReadDatabaseTest {

    @Autowired
    private InterviewReadQuery query;

    @Test
    void sameDateTieBreakTraversesExactlyOnceWithFullTotal() {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            var page = query.list(OWNER, criteria(cursor, null));
            assertThat(page.total()).isEqualTo(3);
            ids.addAll(page.items().stream().map(item -> item.id()).toList());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertThat(ids).containsExactly(R103, R102, R101);
    }

    @Test
    void malformedCursorFailsAndReusedCursorStillAppliesCurrentFilter() {
        assertThatThrownBy(() -> query.list(OWNER, criteria("?", null)))
                .isInstanceOf(IllegalArgumentException.class);
        String cursor = query.list(OWNER, criteria(null, null)).nextCursor();
        var page = query.list(OWNER, criteria(cursor, List.of("private")));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(R101);
    }

    private static InterviewListCriteria criteria(String cursor, List<String> publication) {
        return InterviewListCriteria.from(null, null, null, null, null,
                publication, null, null, cursor, "1");
    }
}
