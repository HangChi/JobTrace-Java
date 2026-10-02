package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.interviews.application.InterviewReadQuery;
import com.jobtrace.interviews.application.ListPrivateInterviews;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import com.jobtrace.interviews.domain.InterviewPage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListPrivateInterviewsTest {

    @Test
    void forwardsTrustedOwnerAndCriteriaWithoutChangingEmptyResult() {
        var query = Mockito.mock(InterviewReadQuery.class);
        var criteria = InterviewListCriteria.from(null, null, null, null, null,
                null, null, null, null, null);
        var page = new InterviewPage(List.of(), null, 0, 50);
        when(query.list("owner-a", criteria)).thenReturn(page);
        assertThat(new ListPrivateInterviews(query).execute("owner-a", criteria)).isSameAs(page);
        verify(query).list("owner-a", criteria);
    }

    @Test
    void rejectsBlankOwnerAndPropagatesQueryFailure() {
        var query = Mockito.mock(InterviewReadQuery.class);
        var criteria = InterviewListCriteria.from(null, null, null, null, null,
                null, null, null, null, null);
        var service = new ListPrivateInterviews(query);
        assertThatThrownBy(() -> service.execute(" ", criteria))
                .isInstanceOf(IllegalArgumentException.class);
        when(query.list(eq("owner-a"), any())).thenThrow(new IllegalStateException("storage"));
        assertThatThrownBy(() -> service.execute("owner-a", criteria))
                .isInstanceOf(IllegalStateException.class);
    }
}
