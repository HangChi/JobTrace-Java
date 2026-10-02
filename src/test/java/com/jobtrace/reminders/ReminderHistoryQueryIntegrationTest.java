package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.ReminderSelection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ReminderReadDatabaseTest.FixedClockConfiguration.class)
class ReminderHistoryQueryIntegrationTest extends ReminderReadDatabaseTest {

    @Autowired
    private ReminderReadQuery query;

    @Test
    void historyFiltersStatesAndOwnerWithoutMixingActiveRows() {
        var completed = query.list(OWNER, ReminderSelection.COMPLETED, READ_INSTANT);
        var cancelled = query.list(OWNER, ReminderSelection.CANCELLED, READ_INSTANT);
        assertThat(completed).extracting(item -> item.id()).containsExactly(R103);
        assertThat(completed.getFirst().completedAt()).isNotNull();
        assertThat(completed.getFirst().cancelledAt()).isNull();
        assertThat(cancelled).extracting(item -> item.id()).containsExactly(R104);
        assertThat(cancelled.getFirst().cancelledAt()).isNotNull();
        assertThat(query.list(OTHER_OWNER, ReminderSelection.COMPLETED, READ_INSTANT)).isEmpty();
    }

    @Test
    void completedHistoryIsBoundedAndOrderedByTimeThenId() {
        for (int index = 0; index < 201; index++) {
            jdbc.update("""
                    insert into scheduled_reminders(id,owner_id,application_id,title,
                      event_at,notify_at,status,completed_at) values
                    (?::uuid,?,?::uuid,'Synthetic','2026-09-25T00:00:00Z',
                      '2026-09-24T00:00:00Z','completed','2026-09-25T01:00:00Z')
                    """, new UUID(0, 1000 + index).toString(), OWNER,
                    "00000000-0000-0000-0000-000000000201");
        }
        var history = query.list(OWNER, ReminderSelection.COMPLETED, READ_INSTANT);
        assertThat(history).hasSize(200);
        assertThat(history.getFirst().id()).isEqualTo(R103);
        assertThat(history.get(1).id()).isEqualTo(new UUID(0, 1000).toString());
        assertThat(history.getLast().id()).isEqualTo(new UUID(0, 1198).toString());
    }
}
