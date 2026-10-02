package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.jobtrace.interviews.application.GetPrivateInterview;
import com.jobtrace.interviews.application.InterviewReadQuery;
import com.jobtrace.interviews.domain.InterviewNotFoundException;
import com.jobtrace.interviews.domain.InterviewDetail;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GetPrivateInterviewTest {

    @Test
    void validatesOwnerAndMapsMissingToUniformNotFound() {
        var query = Mockito.mock(InterviewReadQuery.class);
        var service = new GetPrivateInterview(query);
        UUID id = UUID.fromString(InterviewReadDatabaseTest.R102);
        assertThatThrownBy(() -> service.execute("", id))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute("owner-a", null))
                .isInstanceOf(NullPointerException.class);
        when(query.findDetail("owner-a", id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute("owner-a", id))
                .isInstanceOf(InterviewNotFoundException.class);
        var owned = Mockito.mock(InterviewDetail.class);
        when(query.findDetail("owner-a", id)).thenReturn(Optional.of(owned));
        org.assertj.core.api.Assertions.assertThat(service.execute("owner-a", id)).isSameAs(owned);
    }
}
