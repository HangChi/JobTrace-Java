package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketDetailOwnerIsolationIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private JobMarketReadQuery query;

    @Test
    void trackedApplicationReferencesAreOwnerBoundWithoutChangingSharedContent() {
        var owner = query.findDetail(OWNER, UUID.fromString(CAMPAIGN)).orElseThrow();
        var other = query.findDetail(OTHER_OWNER, UUID.fromString(CAMPAIGN)).orElseThrow();

        assertThat(owner.jobs().getFirst().alreadyTrackedApplicationId())
                .isEqualTo(UUID.fromString(APPLICATION));
        assertThat(other.jobs().getFirst().alreadyTrackedApplicationId()).isNull();
        assertThat(owner.summary().isFavorite()).isTrue();
        assertThat(other.summary().isFavorite()).isFalse();
        assertThat(owner.summary().company()).isEqualTo(other.summary().company());
        assertThat(owner.summary().positions()).isEqualTo(other.summary().positions());
        assertThat(owner.jobs().getFirst().title()).isEqualTo(other.jobs().getFirst().title());
        assertThat(owner.jobs().getFirst().applyUrl()).isEqualTo(other.jobs().getFirst().applyUrl());
    }
}
