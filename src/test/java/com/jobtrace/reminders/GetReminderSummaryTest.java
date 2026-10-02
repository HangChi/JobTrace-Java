package com.jobtrace.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.reminders.application.GetReminderSummary;
import com.jobtrace.reminders.application.ReminderReadQuery;
import com.jobtrace.reminders.domain.ReminderEmailAvailability;
import com.jobtrace.reminders.domain.ReminderSelection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GetReminderSummaryTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Test
    void forwardsTrustedOwnerSelectionAndSingleClockInstant() {
        ReminderReadQuery query = Mockito.mock(ReminderReadQuery.class);
        when(query.list(OWNER, ReminderSelection.ACTIVE, NOW)).thenReturn(List.of());
        when(query.emailAvailability(OWNER)).thenReturn(
                new ReminderEmailAvailability(false, null));

        var summary = new GetReminderSummary(query, Clock.fixed(NOW, ZoneOffset.UTC))
                .execute(OWNER, ReminderSelection.ACTIVE);

        assertThat(summary.overdue()).isEmpty();
        assertThat(summary.upcoming()).isEmpty();
        verify(query).list(OWNER, ReminderSelection.ACTIVE, NOW);
        verify(query).emailAvailability(OWNER);
    }

    @Test
    void rejectsBlankOwnerAndPropagatesQueryFailure() {
        ReminderReadQuery query = Mockito.mock(ReminderReadQuery.class);
        var useCase = new GetReminderSummary(query, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> useCase.execute(" ", ReminderSelection.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
        when(query.list(eq(OWNER), eq(ReminderSelection.ACTIVE), any()))
                .thenThrow(new IllegalStateException("storage"));
        assertThatThrownBy(() -> useCase.execute(OWNER, ReminderSelection.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final String OWNER = "owner-a";
}
