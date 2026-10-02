package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.ReminderCatalog.AttemptStatus;
import com.jobtrace.reminders.domain.ReminderSelection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ReminderReadDatabaseTest.FixedClockConfiguration.class)
class ReminderActiveOwnerIsolationIntegrationTest extends ReminderReadDatabaseTest {

    @Autowired
    private ReminderReadQuery query;

    @Test
    void listAndCurrentScheduleAttemptNeverDiscloseAnotherOwner() {
        var own = query.list(OWNER, ReminderSelection.ACTIVE, READ_INSTANT);
        assertThat(own).extracting(item -> item.id()).containsExactly(R101, R102)
                .doesNotContain(R105, R201);
        assertThat(own).extracting(item -> item.companyName())
                .containsOnly("Example Labs").doesNotContain("Secret Labs");
        assertThat(own.getFirst().emailStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(own.getLast().emailStatus()).isNull();

        var other = query.list(OTHER_OWNER, ReminderSelection.ACTIVE, READ_INSTANT);
        assertThat(other).extracting(item -> item.id()).containsExactly(R201);
        assertThat(query.emailAvailability(OWNER).address()).isEqualTo("owner@example.test");
        assertThat(query.emailAvailability(OTHER_OWNER).address()).isNull();
    }

    @Test
    void missingOwnerHasNoRowsOrAddress() {
        assertThat(query.list(EMPTY_OWNER, ReminderSelection.ACTIVE, READ_INSTANT)).isEmpty();
        assertThat(query.emailAvailability(EMPTY_OWNER).available()).isFalse();
    }
}
