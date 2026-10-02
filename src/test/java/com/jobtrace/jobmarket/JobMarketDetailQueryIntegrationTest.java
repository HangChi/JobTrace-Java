package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.ApplyTargetPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketDetailQueryIntegrationTest extends JobMarketReadDatabaseTest {

    @Autowired
    private JobMarketReadQuery query;

    @Test
    void resolvesCampaignToCompleteCompanySummaryAndOrderedEligibleJobs() {
        jdbc.update("insert into job_market_locations(id,display_name,is_remote) "
                + "values ('60000000-0000-4000-8000-000000000003','Shanghai',false)");
        jdbc.update("insert into job_market_post_locations(post_id,location_id) "
                + "values (?::uuid,'60000000-0000-4000-8000-000000000003')", JOB);

        var detail = query.findDetail(OWNER, UUID.fromString(CAMPAIGN)).orElseThrow();

        assertThat(detail.summary().positions())
                .containsExactly("Backend Engineer", "Frontend Engineer");
        assertThat(detail.jobs()).extracting(job -> job.title())
                .containsExactly("Backend Engineer", "Frontend Engineer");
        assertThat(detail.jobs().getFirst().sourceName()).isEqualTo("greenhouse");
        assertThat(detail.jobs().getFirst().sourceUrl()).isEqualTo("https://jobs.example.test");
        assertThat(detail.jobs().getFirst().locations()).hasSize(1);
        assertThat(detail.jobs()).noneMatch(job -> job.id().toString().equals(CLOSED_JOB));
        assertThat(detail.jobs().get(1).applyUnavailableReason())
                .isEqualTo(ApplyTargetPolicy.EXPIRED_REASON);
    }

    @Test
    void hidesJobsWithoutAnActiveSourceAndSanitizesUnsafeTargets() {
        jdbc.update("update job_market_posts set primary_apply_url='http://unsafe.example.test' "
                + "where id=?::uuid", JOB);
        var unsafe = query.findDetail(OWNER, UUID.fromString(CAMPAIGN)).orElseThrow();
        assertThat(unsafe.jobs().getFirst().applyUrl()).isNull();
        assertThat(unsafe.jobs().getFirst().applyUnavailableReason())
                .isEqualTo(ApplyTargetPolicy.UNSAFE_REASON);

        jdbc.update("update job_market_sources set status='inactive'");
        assertThat(query.findDetail(OWNER, UUID.fromString(CAMPAIGN)).orElseThrow().jobs())
                .isEmpty();
    }

    @Test
    void directoryRepresentativeHasNoSyntheticJobsAndMissingOrClosedIsIneligible() {
        jdbc.update("update job_market_company_read_models set "
                + "representative_campaign_id=?::uuid, listing_kind='recruitment_directory', "
                + "recruitment_type='招聘官网', positions=array[]::text[], position_count=0 "
                + "where company_id=?::uuid and include_closed=false", DIRECTORY, COMPANY);

        var directory = query.findDetail(OWNER, UUID.fromString(DIRECTORY)).orElseThrow();
        assertThat(directory.summary().id()).isEqualTo(UUID.fromString(DIRECTORY));
        assertThat(directory.summary().recruitmentType()).isEqualTo("招聘官网");
        assertThat(directory.jobs()).isEmpty();
        assertThat(query.findDetail(OWNER, UUID.randomUUID())).isEmpty();
        assertThat(query.findDetail(OWNER, UUID.fromString(CLOSED_CAMPAIGN))).isEmpty();
    }
}
