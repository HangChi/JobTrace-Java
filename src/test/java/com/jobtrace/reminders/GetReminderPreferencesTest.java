package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.reminders.application.GetReminderPreferences;
import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.ReminderPreferences;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GetReminderPreferencesTest {

    @Test
    void requiresTrustedOwnerAndForwardsOnlyIt() {
        ReminderReadQuery query = Mockito.mock(ReminderReadQuery.class);
        var useCase = new GetReminderPreferences(query);
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(" "))
                .isInstanceOf(IllegalArgumentException.class);
        when(query.preferences("owner-a")).thenReturn(ReminderPreferences.defaults());
        assertThat(useCase.execute("owner-a")).isEqualTo(ReminderPreferences.defaults());
        verify(query).preferences("owner-a");
    }

    @Test
    void rejectsInvalidPersistedPreferenceValues() {
        assertThatThrownBy(() -> new ReminderPreferences(true, "invalid", "1h", 30, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderPreferences(true, "scheduled", "invalid", 30,
                false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderPreferences(true, "scheduled", "1h", 45, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
