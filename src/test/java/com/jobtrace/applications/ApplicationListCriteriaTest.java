package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationSort;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType;
import com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage;
import com.jobtrace.applications.domain.ApplicationCatalog.SortDirection;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationListCriteriaTest {

    @Test
    void normalizesBoundsAndIgnoresUnknownOptionalValues() {
        String longQuery = "  " + "x".repeat(205) + "  ";
        String longCity = "城".repeat(105);

        ApplicationListCriteria criteria = ApplicationListCriteria.from(
                longQuery,
                List.of("submitted", "unknown"),
                List.of("spring_recruitment", "unknown"),
                List.of("assessment", "unknown"),
                List.of(longCity),
                "not-a-date",
                "2026-10-02",
                "unknown",
                "unknown",
                null,
                "-3",
                "1000");

        assertThat(criteria.query()).hasSize(200);
        assertThat(criteria.statuses()).containsExactly(ApplicationStatus.SUBMITTED);
        assertThat(criteria.types()).containsExactly(ApplicationType.SPRING_RECRUITMENT);
        assertThat(criteria.stages()).containsExactly(RecruitmentStage.ASSESSMENT);
        assertThat(criteria.cities()).containsExactly("城".repeat(100));
        assertThat(criteria.appliedFrom()).isNull();
        assertThat(criteria.appliedTo()).hasToString("2026-10-02");
        assertThat(criteria.sort()).isEqualTo(ApplicationSort.LATEST_DATE);
        assertThat(criteria.defaultOrder()).isTrue();
        assertThat(criteria.direction()).isEqualTo(SortDirection.DESC);
        assertThat(criteria.page()).isEqualTo(1);
        assertThat(criteria.limit()).isEqualTo(100);
    }

    @Test
    void preservesExplicitSortAndUsesItsDefaultDirection() {
        ApplicationListCriteria criteria = ApplicationListCriteria.from(
                "  ",
                null,
                null,
                null,
                null,
                null,
                null,
                "company",
                "desc",
                "  ",
                "2",
                "0");

        assertThat(criteria.query()).isNull();
        assertThat(criteria.statuses()).isEmpty();
        assertThat(criteria.types()).isEmpty();
        assertThat(criteria.stages()).isEmpty();
        assertThat(criteria.cities()).isEmpty();
        assertThat(criteria.sort()).isEqualTo(ApplicationSort.COMPANY);
        assertThat(criteria.defaultOrder()).isFalse();
        assertThat(criteria.direction()).isEqualTo(SortDirection.DESC);
        assertThat(criteria.cursor()).isNull();
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.limit()).isEqualTo(1);
    }
}
