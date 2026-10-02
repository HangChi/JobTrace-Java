package com.jobtrace.applicationdialog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.applicationdialog.application.GetApplicationDialogData;
import com.jobtrace.applications.application.GetApplicationDetail;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.interviews.application.ListInterviewsForApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GetApplicationDialogDataTest {

    @Test
    void checksOwnedApplicationBeforeReadingInterviews() {
        var getApplication = Mockito.mock(GetApplicationDetail.class);
        var listInterviews = Mockito.mock(ListInterviewsForApplication.class);
        var application = Mockito.mock(ApplicationDetail.class);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000201");
        when(getApplication.execute("owner-a", id)).thenReturn(application);
        when(listInterviews.execute("owner-a", id)).thenReturn(List.of());
        var dialog = new GetApplicationDialogData(getApplication, listInterviews);
        dialog.execute("owner-a", id);
        var order = inOrder(getApplication, listInterviews);
        order.verify(getApplication).execute("owner-a", id);
        order.verify(listInterviews).execute("owner-a", id);
        when(getApplication.execute("owner-b", id)).thenThrow(new ApplicationNotFoundException());
        assertThatThrownBy(() -> dialog.execute("owner-b", id))
                .isInstanceOf(ApplicationNotFoundException.class);
        verify(listInterviews, never()).execute("owner-b", id);
    }
}
