package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketListQueryIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private JobMarketReadQuery query;

    @Test
    void defaultProjectionExcludesClosedAndPreservesPageMetadata() {
        var page = query.list(OWNER, MarketplaceQuery.defaults());
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.company().name()).isEqualTo("Example Labs");
        assertThat(item.positions()).containsExactly("Backend Engineer", "Frontend Engineer");
        assertThat(item.positionCount()).isEqualTo(2);
        assertThat(item.publishedAt()).isEqualTo("2026-09-02T00:00:00.000Z");
        assertThat(item.isFavorite()).isTrue();

        var beyond = query.list(OWNER, new MarketplaceQuery(
                null, null, null, null, null, null, 2, 20));
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(1);
    }

    @Test
    void listPreviewStopsAtFiftyButCountRemainsComplete() {
        String[] values = new String[51];
        for (int index = 0; index < values.length; index++) {
            values[index] = "Role " + index;
        }
        jdbc.update("update job_market_company_read_models set positions=?, position_count=51 "
                + "where company_id=?::uuid and include_closed=false", values, COMPANY);
        var item = query.list(OWNER, MarketplaceQuery.defaults()).items().getFirst();
        assertThat(item.positions()).hasSize(50);
        assertThat(item.positionCount()).isEqualTo(51);
    }
}
