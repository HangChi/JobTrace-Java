package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.domain.InterviewListCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewListCriteriaTest {

    @Test
    void normalizesTrimAndRepeatedFilters() {
        var criteria = InterviewListCriteria.from(null, "  Platform  ",
                List.of("", "draft", "completed"), List.of("assessment"),
                List.of("passed"), List.of("", "private", "anonymous"),
                "2026-09-01", "2026-09-30", null, null);
        assertThat(criteria.query()).isEqualTo("Platform");
        assertThat(criteria.statuses()).hasSize(2);
        assertThat(criteria.publications()).hasSize(2);
        assertThat(criteria.interviewedFrom()).hasToString("2026-09-01");
        assertThat(criteria.interviewedTo()).hasToString("2026-09-30");
        assertThat(criteria.limit()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidValuesInsteadOfSilentlyIgnoringThem() {
        assertThatThrownBy(() -> empty("bad", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> empty(null, "not-a-date", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> empty(null, null, "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> empty(null, null, "101")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterviewListCriteria.from("not-uuid", null,
                null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterviewListCriteria.from(null, "x".repeat(201),
                null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InterviewListCriteria empty(String status, String from, String limit) {
        return InterviewListCriteria.from(null, null,
                status == null ? null : List.of(status), null, null, null,
                from, null, null, limit);
    }
}
