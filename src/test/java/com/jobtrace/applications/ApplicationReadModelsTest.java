package com.jobtrace.applications;

import static com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus.SUBMITTED;
import static com.jobtrace.applications.domain.ApplicationCatalog.ApplicationType.SPRING_RECRUITMENT;
import static com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage.ASSESSMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationDetail.ApplicationEvent;
import com.jobtrace.applications.domain.ApplicationDetail.StageOccurrence;
import com.jobtrace.applications.domain.ApplicationPage;
import com.jobtrace.applications.domain.ApplicationSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationReadModelsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

    @Test
    void snapshotsMutableCollectionsAtTheDomainBoundary() {
        List<com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage> stages =
                new ArrayList<>(List.of(ASSESSMENT));
        ApplicationSummary summary = summary(stages);
        List<ApplicationSummary> items = new ArrayList<>(List.of(summary));
        ApplicationPage page = new ApplicationPage(items, "next", 1, 1, 50);

        stages.clear();
        items.clear();

        assertThat(summary.stages()).containsExactly(ASSESSMENT);
        assertThat(page.items()).containsExactly(summary);
        assertThatThrownBy(() -> summary.stages().add(ASSESSMENT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructsTheCompleteLegacyDetailShape() {
        StageOccurrence occurrence = new StageOccurrence("stage-1", ASSESSMENT, DATE);
        ApplicationEvent event = new ApplicationEvent(
                "event-1", "stage_changed", DATE, Map.of("stage", "screening"),
                Map.of("stage", "assessment"), "2026-09-10T08:00:00Z");
        ApplicationDetail detail = new ApplicationDetail(
                "application-1", "Acme", "Engineer", "Shanghai", "https://example.com/job",
                DATE, SPRING_RECRUITMENT, SUBMITTED, DATE, List.of(ASSESSMENT), true, 3,
                "follow up", 1, "notes", List.of(occurrence), List.of(event),
                "2026-09-10T08:00:00Z", "2026-09-10T09:00:00Z");

        assertThat(detail.stageOccurrences()).containsExactly(occurrence);
        assertThat(detail.events()).containsExactly(event);
        assertThat(detail.followUpDays()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidReadModelMetadata() {
        assertThatThrownBy(() -> summary(List.of(), -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary(List.of(), 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationPage(List.of(), null, -1, 1, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationPage(List.of(), null, 0, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationPage(List.of(), null, 0, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDetail(
                        "application-1", "Acme", "Engineer", null, null, DATE,
                        SPRING_RECRUITMENT, SUBMITTED, DATE, List.of(), false, -1, null,
                        1, null, List.of(), List.of(), "created", "updated"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationSummary summary(
            List<com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage> stages) {
        return summary(stages, 0, 1);
    }

    private static ApplicationSummary summary(
            List<com.jobtrace.applications.domain.ApplicationCatalog.RecruitmentStage> stages,
            int followUpDays,
            int version) {
        return new ApplicationSummary(
                "application-1", "Acme", "Engineer", "Shanghai", null, DATE,
                SPRING_RECRUITMENT, SUBMITTED, DATE, stages, false, followUpDays, null, version);
    }
}
