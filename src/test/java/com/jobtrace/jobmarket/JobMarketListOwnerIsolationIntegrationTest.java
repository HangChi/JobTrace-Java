package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketListOwnerIsolationIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private JobMarketReadQuery query;

    @Test
    void favoriteStateIsOwnerBoundWhileSharedContentIsIdentical() {
        var ownerItem = query.list(OWNER, MarketplaceQuery.defaults()).items().getFirst();
        var otherItem = query.list(OTHER_OWNER, MarketplaceQuery.defaults()).items().getFirst();
        assertThat(ownerItem.isFavorite()).isTrue();
        assertThat(otherItem.isFavorite()).isFalse();
        assertThat(otherItem.company()).isEqualTo(ownerItem.company());
        assertThat(otherItem.positions()).isEqualTo(ownerItem.positions());
        assertThat(otherItem.primaryApplyUrl()).isEqualTo(ownerItem.primaryApplyUrl());
    }
}
