package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketFilterQueryIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private JobMarketReadQuery query;

    @Test
    void appliesTextCompanyLocationStatusAndInclusiveDateConjunctively() {
        var result = query.list(OWNER, new MarketplaceQuery(
                "BACKEND", "example LABS", "SHANGHAI", PostStatus.OPEN,
                LocalDate.of(2026, 9, 2), null, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting(item -> item.company().name())
                .containsExactly("Example Labs");
        assertThat(query.list(OWNER, new MarketplaceQuery(
                "backend", null, null, null, LocalDate.of(2026, 9, 3),
                null, 1, 20)).items()).isEmpty();
    }

    @Test
    void favoriteTrueIncludesClosedOnlyForTheTrustedOwner() {
        jdbc.update("insert into job_market_campaign_favorites(owner_id,campaign_id) "
                + "values (?,?::uuid)", OWNER, CLOSED_CAMPAIGN);

        var ownerPage = query.list(OWNER, new MarketplaceQuery(
                null, null, null, null, null, true, 1, 20));
        var otherPage = query.list(OTHER_OWNER, new MarketplaceQuery(
                null, null, null, null, null, true, 1, 20));

        assertThat(ownerPage.total()).isEqualTo(2);
        assertThat(ownerPage.items()).extracting(item -> item.company().name())
                .containsExactly("Example Labs", "Closed Corp");
        assertThat(otherPage.items()).isEmpty();
    }

    @Test
    void explicitClosedStatusUsesClosedProjectionAndStablePageBoundaries() {
        var closed = query.list(OWNER, new MarketplaceQuery(
                null, null, null, PostStatus.CLOSED, null, null, 1, 1));
        assertThat(closed.total()).isEqualTo(1);
        assertThat(closed.items()).extracting(item -> item.company().name())
                .containsExactly("Closed Corp");

        var secondPage = query.list(OWNER, new MarketplaceQuery(
                null, null, null, PostStatus.CLOSED, null, null, 2, 1));
        assertThat(secondPage.total()).isEqualTo(1);
        assertThat(secondPage.items()).isEmpty();
    }

    @Test
    void favoriteFalseBehavesAsOmittedAndInactiveSourcesCannotCreateListRows() {
        jdbc.update("update job_market_sources set status='inactive'");
        var omitted = query.list(EMPTY_OWNER, MarketplaceQuery.defaults());
        var explicitFalse = query.list(EMPTY_OWNER, new MarketplaceQuery(
                null, null, null, null, null, false, 1, 20));

        assertThat(explicitFalse).isEqualTo(omitted);
        assertThat(explicitFalse.items()).hasSize(1);
    }
}
