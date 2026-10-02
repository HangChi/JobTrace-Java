package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.domain.InterviewCatalog.AuthorMode;
import com.jobtrace.interviews.domain.InterviewCatalog.QuestionCategory;
import com.jobtrace.interviews.domain.InterviewCatalog.Result;
import com.jobtrace.interviews.domain.InterviewCatalog.Stage;
import com.jobtrace.interviews.domain.InterviewCatalog.Status;
import com.jobtrace.interviews.domain.InterviewCatalog.Visibility;
import com.jobtrace.interviews.domain.InterviewPage;
import com.jobtrace.interviews.domain.InterviewQuestion;
import com.jobtrace.interviews.domain.InterviewSummary;
import com.jobtrace.interviews.domain.StageInterviewSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewReadModelsTest {

    @Test
    void rejectsInconsistentLinkAndNegativeSummaryCounts() {
        assertThatThrownBy(() -> summary(null, true, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary("stage-id", false, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary(null, false, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary(null, false, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(summary(null, false, 0, 0).linked()).isFalse();
    }

    @Test
    void pageAndStageSummaryRejectInvalidMetadataAndCopyCollections() {
        assertThatThrownBy(() -> new InterviewPage(List.of(), null, -1, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewPage(List.of(), null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewPage(List.of(), null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        List<InterviewSummary> source = new ArrayList<>();
        source.add(summary(null, false, 0, 0));
        var page = new InterviewPage(source, null, 1, 50);
        source.clear();
        assertThat(page.items()).hasSize(1);
        assertThatThrownBy(() -> new StageInterviewSummary("id", Stage.ASSESSMENT,
                LocalDate.of(2026, 9, 20), Status.DRAFT, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void questionRatingSupportsNullAndRejectsOutOfRangeValues() {
        assertThat(new InterviewQuestion("id", QuestionCategory.OTHER, "Question",
                null, null, null, null).selfRating()).isNull();
        assertThatThrownBy(() -> new InterviewQuestion("id", QuestionCategory.OTHER,
                "Question", null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewQuestion("id", QuestionCategory.OTHER,
                "Question", null, null, null, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InterviewSummary summary(
            String stageOccurrenceId, boolean linked, int questions, int actions) {
        return new InterviewSummary("review", "application", stageOccurrenceId,
                "Example Labs", "Engineer", Stage.ASSESSMENT,
                LocalDate.of(2026, 9, 20), Status.DRAFT, Result.PENDING,
                linked, questions, actions, Visibility.PRIVATE, AuthorMode.ANONYMOUS, null);
    }
}
