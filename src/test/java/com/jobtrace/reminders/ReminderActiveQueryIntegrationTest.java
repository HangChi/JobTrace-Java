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
class ReminderActiveQueryIntegrationTest extends ReminderReadDatabaseTest {

    @Autowired
    private ReminderReadQuery query;

    @Test
    void exactBoundaryAndReadOnlyGroupingPreserveStoredPendingState() {
        var rows = query.list(OWNER, ReminderSelection.ACTIVE, READ_INSTANT);
        assertThat(rows).extracting(item -> item.id()).containsExactly(R101, R102);
        assertThat(rows.getFirst().dueAt(READ_INSTANT)).isTrue();
        assertThat(rows.getLast().dueAt(READ_INSTANT)).isFalse();
        assertThat(jdbc.queryForObject("select status from scheduled_reminders where id=?::uuid",
                String.class, R101)).isEqualTo("pending");
    }

    @Test
    void sameTimeUsesUuidTieBreakAndOverviewStopsAtTwoHundred() {
        for (int index = 0; index < 201; index++) {
            String id = new UUID(0, 1000 + index).toString();
            jdbc.update("""
                    insert into scheduled_reminders(id,owner_id,application_id,title,
                      event_at,notify_at,status) values
                    (?::uuid,?,?::uuid,'Synthetic','2026-10-05T00:00:00Z',
                      '2026-10-04T00:00:00Z','pending')
                    """, id, OWNER, "00000000-0000-0000-0000-000000000201");
        }
        var rows = query.list(OWNER, ReminderSelection.ACTIVE, READ_INSTANT);
        assertThat(rows).hasSize(200);
        assertThat(rows.get(0).id()).isEqualTo(R101);
        assertThat(rows.get(1).id()).isEqualTo(R102);
        assertThat(rows.get(2).id()).isEqualTo(new UUID(0, 1000).toString());
        assertThat(rows.getLast().id()).isEqualTo(new UUID(0, 1197).toString());
    }
}
