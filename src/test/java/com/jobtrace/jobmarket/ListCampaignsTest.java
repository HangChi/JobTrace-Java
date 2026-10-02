package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.application.ListCampaigns;
import com.jobtrace.jobmarket.domain.CampaignPage;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListCampaignsTest {

    @Test
    void requiresTrustedOwnerAndPropagatesImmutablePage() {
        JobMarketReadQuery query = mock(JobMarketReadQuery.class);
        MarketplaceQuery criteria = MarketplaceQuery.defaults();
        CampaignPage expected = new CampaignPage(List.of(), 1, 20, 0);
        when(query.list("owner-a", criteria)).thenReturn(expected);
        var useCase = new ListCampaigns(query);

        assertThat(useCase.execute("owner-a", criteria)).isSameAs(expected);
        verify(query).list("owner-a", criteria);
        assertThatThrownBy(() -> useCase.execute(" ", criteria))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
