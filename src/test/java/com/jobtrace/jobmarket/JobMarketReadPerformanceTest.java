package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.GetCampaignDetail;
import com.jobtrace.jobmarket.application.ListCampaigns;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import com.jobtrace.jobmarket.infrastructure.PostgresJobMarketReadQuery;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import tools.jackson.databind.ObjectMapper;

class JobMarketReadPerformanceTest extends JobMarketReadDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void hundredCompanyHundredThousandJobDatasetMeetsBudgetsAndQueryBounds() {
        seedRepresentativeLoad();
        assertThat(jdbc.queryForObject("select count(*) from job_market_companies", Integer.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from job_market_posts", Integer.class))
                .isEqualTo(100_000);

        var counted = new CountingTemplate(dataSource);
        var adapter = new PostgresJobMarketReadQuery(counted, mapper);
        var list = new ListCampaigns(adapter);
        var detail = new GetCampaignDetail(adapter);
        for (int index = 0; index < 10; index++) {
            serialize(list.execute(OWNER, MarketplaceQuery.defaults()));
            serialize(detail.execute(OWNER, java.util.UUID.fromString(CAMPAIGN)));
        }

        long[] listMs = new long[40];
        long[] detailMs = new long[40];
        for (int index = 0; index < 40; index++) {
            counted.reset();
            listMs[index] = timed(() -> serialize(
                    list.execute(OWNER, MarketplaceQuery.defaults())));
            assertThat(counted.count()).isEqualTo(2);

            counted.reset();
            detailMs[index] = timed(() -> serialize(
                    detail.execute(OTHER_OWNER, java.util.UUID.fromString(CAMPAIGN))));
            assertThat(counted.count()).isEqualTo(3);
        }
        Arrays.sort(listMs);
        Arrays.sort(detailMs);
        assertThat(listMs[37]).isLessThanOrEqualTo(500);
        assertThat(detailMs[37]).isLessThanOrEqualTo(500);
        System.out.printf("008 read p95 ms: list=%d detail=%d; queries=2/3%n",
                listMs[37], detailMs[37]);
    }

    private void seedRepresentativeLoad() {
        jdbc.update("""
                insert into job_market_companies(
                  id,canonical_name,company_type,industry,website_url)
                select md5('perf-company-' || n)::uuid, 'Company ' || n,
                  'private','software','https://jobs.example.test'
                from generate_series(2,99) n
                """);
        jdbc.update("""
                insert into job_market_campaigns(id,company_id,listing_kind,status)
                select md5('perf-campaign-' || n)::uuid,
                  md5('perf-company-' || n)::uuid,'synced_jobs','open'
                from generate_series(2,99) n;
                insert into job_market_sources(
                  id,company_id,adapter,base_url,is_official,status,last_success_at)
                select md5('perf-source-' || n)::uuid,
                  md5('perf-company-' || n)::uuid,'greenhouse',
                  'https://jobs.example.test',true,'active',now()
                from generate_series(2,99) n;
                insert into job_market_company_read_models(
                  company_id,include_closed,representative_campaign_id,listing_kind,
                  positions,position_count,locations,status,primary_apply_url,
                  source_name,source_url,published_at,last_confirmed_at,search_text,location_text)
                select md5('perf-company-' || n)::uuid,false,
                  md5('perf-campaign-' || n)::uuid,'synced_jobs',array['Engineer'],1,'[]',
                  'open','https://jobs.example.test','greenhouse',
                  'https://jobs.example.test','2026-09-01T00:00:00Z',
                  '2026-09-02T00:00:00Z','company engineer',''
                from generate_series(2,99) n
                """);
        jdbc.update("""
                insert into job_market_posts(
                  id,company_id,campaign_id,title,status,primary_apply_url,published_at)
                select md5('perf-job-' || n)::uuid,
                  case when company_number = 1 then ?::uuid
                    else md5('perf-company-' || company_number)::uuid end,
                  case when company_number = 1 then ?::uuid
                    else md5('perf-campaign-' || company_number)::uuid end,
                  'Engineer ' || n,'open','https://jobs.example.test/apply/' || n,
                  '2026-09-01T00:00:00Z'::timestamptz - (n || ' seconds')::interval
                from (select n, ((n - 1) % 99) + 1 as company_number
                  from generate_series(1,99997) n) generated
                """, COMPANY, CAMPAIGN);
        jdbc.update("""
                insert into job_market_source_records(source_id,post_id,last_seen_at)
                select case when company_number = 1
                    then '20000000-0000-4000-8000-000000000001'::uuid
                    else md5('perf-source-' || company_number)::uuid end,
                  md5('perf-job-' || n)::uuid,'2026-09-03T00:00:00Z'
                from (select n, ((n - 1) % 99) + 1 as company_number
                  from generate_series(1,99997) n) generated
                """);
        jdbc.execute("analyze job_market_companies; analyze job_market_campaigns; "
                + "analyze job_market_company_read_models; analyze job_market_posts; "
                + "analyze job_market_source_records");
    }

    private void serialize(Object value) {
        try {
            mapper.writeValueAsBytes(value);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private static long timed(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static final class CountingTemplate extends NamedParameterJdbcTemplate {
        private int count;

        private CountingTemplate(DataSource dataSource) {
            super(dataSource);
        }

        void reset() {
            count = 0;
        }

        int count() {
            return count;
        }

        @Override
        public <T> java.util.List<T> query(
                String sql, SqlParameterSource parameters, RowMapper<T> mapper) {
            count++;
            return super.query(sql, parameters, mapper);
        }

        @Override
        public <T> T queryForObject(
                String sql, SqlParameterSource parameters, Class<T> requiredType) {
            count++;
            return super.queryForObject(sql, parameters, requiredType);
        }
    }
}
