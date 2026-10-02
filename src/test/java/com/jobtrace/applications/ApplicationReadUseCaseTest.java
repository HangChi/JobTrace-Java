package com.jobtrace.applications;

import static com.jobtrace.shared.config.TimeConfiguration.BUSINESS_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.applications.application.ApplicationReadQuery;
import com.jobtrace.applications.application.GetApplicationDetail;
import com.jobtrace.applications.application.ListApplications;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.applications.domain.ApplicationPage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationReadUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-02T01:00:00Z"), BUSINESS_ZONE);
    private static final ApplicationListCriteria CRITERIA = ApplicationListCriteria.from(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    @Test
    void listRequiresAnOwnerAndPassesTheBusinessDate() {
        RecordingQuery query = new RecordingQuery();
        ListApplications useCase = new ListApplications(query, CLOCK);

        assertThatThrownBy(() -> useCase.execute(" ", CRITERIA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(useCase.execute("owner-a", CRITERIA)).isEqualTo(query.page);
        assertThat(query.ownerId).isEqualTo("owner-a");
        assertThat(query.today).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void detailMapsMissingAndInaccessibleRowsToNotFound() {
        RecordingQuery query = new RecordingQuery();
        GetApplicationDetail useCase = new GetApplicationDetail(query, CLOCK);
        UUID id = UUID.fromString("10000000-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> useCase.execute("owner-a", id))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("The application was not found.");
        assertThat(query.ownerId).isEqualTo("owner-a");
        assertThat(query.today).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void detailValidatesItsOwnerAndIdentifier() {
        GetApplicationDetail useCase = new GetApplicationDetail(new RecordingQuery(), CLOCK);

        assertThatThrownBy(() -> useCase.execute(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute("owner-a", null))
                .isInstanceOf(NullPointerException.class);
    }

    private static final class RecordingQuery implements ApplicationReadQuery {

        private final ApplicationPage page = new ApplicationPage(List.of(), null, 0, 1, 50);
        private String ownerId;
        private LocalDate today;

        @Override
        public ApplicationPage list(String ownerId, ApplicationListCriteria criteria, LocalDate today) {
            this.ownerId = ownerId;
            this.today = today;
            return page;
        }

        @Override
        public Optional<ApplicationDetail> findDetail(String ownerId, UUID id, LocalDate today) {
            this.ownerId = ownerId;
            this.today = today;
            return Optional.empty();
        }
    }
}
