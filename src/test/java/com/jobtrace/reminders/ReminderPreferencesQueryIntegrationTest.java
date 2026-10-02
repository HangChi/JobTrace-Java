package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.ReminderPreferences;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ReminderReadDatabaseTest.FixedClockConfiguration.class)
class ReminderPreferencesQueryIntegrationTest extends ReminderReadDatabaseTest {

    @Autowired
    private ReminderReadQuery query;

    @Test
    void readsOnlyOwnersFiveValuesAndLeavesProfileUnchanged() {
        ReminderPreferences before = query.preferences(OWNER);
        assertThat(before).isEqualTo(new ReminderPreferences(
                false, "suggestions", "30m", 60, true));
        assertThat(query.preferences(OTHER_OWNER)).isEqualTo(ReminderPreferences.defaults());
        assertThat(query.preferences(EMPTY_OWNER)).isEqualTo(ReminderPreferences.defaults());
        assertThat(query.preferences("missing-owner")).isEqualTo(ReminderPreferences.defaults());
        assertThat(query.preferences(OWNER)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select reminder_home_view from users where id=?",
                String.class, OWNER)).isEqualTo("suggestions");
    }
}
