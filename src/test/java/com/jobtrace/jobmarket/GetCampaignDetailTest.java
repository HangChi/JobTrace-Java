package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.jobmarket.application.GetCampaignDetail;
import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.CampaignDetail;
import com.jobtrace.shared.web.Problem;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCampaignDetailTest {

    private static final UUID ID = UUID.fromString("30000000-0000-4000-8000-000000000001");

    @Test
    void requiresTrustedOwnerAndReturnsTheQueryResult() {
        JobMarketReadQuery query = mock(JobMarketReadQuery.class);
        CampaignDetail expected = mock(CampaignDetail.class);
        when(query.findDetail("owner-a", ID)).thenReturn(Optional.of(expected));
        var useCase = new GetCampaignDetail(query);

        assertThat(useCase.execute("owner-a", ID)).isSameAs(expected);
        verify(query).findDetail("owner-a", ID);
        assertThatThrownBy(() -> useCase.execute(" ", ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute("owner-a", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mapsMissingOrIneligibleCampaignToSafeNotFound() {
        JobMarketReadQuery query = mock(JobMarketReadQuery.class);
        when(query.findDetail("owner-a", ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetCampaignDetail(query).execute("owner-a", ID))
                .isInstanceOfSatisfying(Problem.class, problem -> {
                    assertThat(problem.code()).isEqualTo("not_found");
                    assertThat(problem.getMessage()).doesNotContain(ID.toString());
                });
    }

    @Test
    void propagatesQueryFailuresWithoutPartialResults() {
        JobMarketReadQuery query = mock(JobMarketReadQuery.class);
        RuntimeException failure = new RuntimeException("storage failed");
        when(query.findDetail("owner-a", ID)).thenThrow(failure);

        assertThatThrownBy(() -> new GetCampaignDetail(query).execute("owner-a", ID))
                .isSameAs(failure);
    }
}
