package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.testing.PostgresIntegrationTest;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
public class JobMarketReadDatabaseTest extends PostgresIntegrationTest {

    protected static final String OWNER = "owner-a";
    protected static final String OTHER_OWNER = "owner-b";
    protected static final String EMPTY_OWNER = "owner-c";
    protected static final String COMPANY = "10000000-0000-4000-8000-000000000001";
    protected static final String CLOSED_COMPANY = "10000000-0000-4000-8000-000000000002";
    protected static final String CAMPAIGN = "30000000-0000-4000-8000-000000000001";
    protected static final String DIRECTORY = "30000000-0000-4000-8000-000000000002";
    protected static final String CLOSED_CAMPAIGN = "30000000-0000-4000-8000-000000000003";
    protected static final String JOB = "40000000-0000-4000-8000-000000000001";
    protected static final String STALE_JOB = "40000000-0000-4000-8000-000000000002";
    protected static final String CLOSED_JOB = "40000000-0000-4000-8000-000000000003";
    protected static final String APPLICATION = "50000000-0000-4000-8000-000000000001";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    protected static RequestPostProcessor asOwner(String owner) {
        var authentication = new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new BridgeIdentity(owner, BridgeIdentity.Role.USER, 3));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    @BeforeEach
    void seedJobMarket() {
        jdbc.execute("drop table if exists application_job_market_links, "
                + "job_market_campaign_favorites, job_market_post_locations, "
                + "job_market_locations, job_market_source_records, job_market_posts, "
                + "job_market_company_read_models, job_market_sources, job_market_campaigns, "
                + "job_market_companies, applications, users cascade");
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/job-market-read-model.sql")).execute(dataSource);
        jdbc.update("insert into users(id) values (?),(?),(?)",
                OWNER, OTHER_OWNER, EMPTY_OWNER);
        jdbc.update("insert into applications(id,owner_id) values (?::uuid,?)",
                APPLICATION, OWNER);
        jdbc.update("""
                insert into job_market_companies(id,canonical_name,company_type,industry,website_url)
                values (?::uuid,'Example Labs','private','software','https://jobs.example.test'),
                  (?::uuid,'Closed Corp','public','finance','https://closed.example.test')
                """, COMPANY, CLOSED_COMPANY);
        jdbc.update("""
                insert into job_market_campaigns(id,company_id,listing_kind,recruitment_type,status)
                values (?::uuid,?::uuid,'synced_jobs',null,'open'),
                  (?::uuid,?::uuid,'recruitment_directory','招聘官网','open'),
                  (?::uuid,?::uuid,'synced_jobs',null,'closed')
                """, CAMPAIGN, COMPANY, DIRECTORY, COMPANY, CLOSED_CAMPAIGN, CLOSED_COMPANY);
        jdbc.update("""
                insert into job_market_sources(id,company_id,adapter,base_url,is_official,status,last_success_at)
                values ('20000000-0000-4000-8000-000000000001',?::uuid,'greenhouse',
                  'https://jobs.example.test',true,'active','2026-09-03T00:00:00Z'),
                  ('20000000-0000-4000-8000-000000000002',?::uuid,'html_list',
                  'https://mirror.example.test',false,'active','2026-09-04T00:00:00Z')
                """, COMPANY, COMPANY);
        jdbc.update("""
                insert into job_market_posts(id,company_id,campaign_id,title,status,
                  primary_apply_url,published_at,valid_through) values
                (?::uuid,?::uuid,?::uuid,'Backend Engineer','open',
                  'https://jobs.example.test/apply/1','2026-09-02T00:00:00Z',null),
                (?::uuid,?::uuid,?::uuid,'Frontend Engineer','stale',
                  'https://jobs.example.test/apply/2','2026-09-01T00:00:00Z',null),
                (?::uuid,?::uuid,?::uuid,'Old Engineer','closed',
                  'https://jobs.example.test/apply/3','2026-08-01T00:00:00Z',null)
                """, JOB, COMPANY, CAMPAIGN, STALE_JOB, COMPANY, CAMPAIGN,
                CLOSED_JOB, COMPANY, CAMPAIGN);
        jdbc.update("""
                insert into job_market_source_records(source_id,post_id,last_seen_at) values
                ('20000000-0000-4000-8000-000000000001',?::uuid,'2026-09-03T00:00:00Z'),
                ('20000000-0000-4000-8000-000000000002',?::uuid,'2026-09-04T00:00:00Z'),
                ('20000000-0000-4000-8000-000000000001',?::uuid,'2026-09-03T00:00:00Z'),
                ('20000000-0000-4000-8000-000000000001',?::uuid,'2026-09-03T00:00:00Z')
                """, JOB, JOB, STALE_JOB, CLOSED_JOB);
        jdbc.update("""
                insert into job_market_locations(id,display_name,is_remote) values
                ('60000000-0000-4000-8000-000000000001','Shanghai',false),
                ('60000000-0000-4000-8000-000000000002','Remote',true)
                """);
        jdbc.update("""
                insert into job_market_post_locations(post_id,location_id) values
                (?::uuid,'60000000-0000-4000-8000-000000000001'),
                (?::uuid,'60000000-0000-4000-8000-000000000002')
                """, JOB, STALE_JOB);
        jdbc.update("""
                insert into job_market_company_read_models(
                  company_id,include_closed,representative_campaign_id,listing_kind,
                  recruitment_type,positions,position_count,locations,status,primary_apply_url,
                  source_name,source_url,published_at,valid_through,last_confirmed_at,
                  search_text,location_text) values
                (?::uuid,false,?::uuid,'synced_jobs',null,
                  array['Backend Engineer','Frontend Engineer'],2,
                  '[{"name":"Remote","isRemote":true},{"name":"Shanghai","isRemote":false}]',
                  'open','https://jobs.example.test','greenhouse','https://jobs.example.test',
                  '2026-09-02T00:00:00Z',null,'2026-09-03T00:00:00Z',
                  'example labs backend engineer frontend engineer','remote shanghai'),
                (?::uuid,true,?::uuid,'synced_jobs',null,
                  array['Backend Engineer','Frontend Engineer','Old Engineer'],3,
                  '[{"name":"Remote","isRemote":true},{"name":"Shanghai","isRemote":false}]',
                  'open','https://jobs.example.test','greenhouse','https://jobs.example.test',
                  '2026-09-02T00:00:00Z',null,'2026-09-03T00:00:00Z',
                  'example labs backend engineer frontend engineer old engineer','remote shanghai'),
                (?::uuid,true,?::uuid,'synced_jobs',null,array['Closed Role'],1,'[]',
                  'closed','http://unsafe.example.test','html_list','http://unsafe.example.test',
                  null,null,null,'closed corp closed role','')
                """, COMPANY, CAMPAIGN, COMPANY, CAMPAIGN, CLOSED_COMPANY, CLOSED_CAMPAIGN);
        jdbc.update("insert into job_market_campaign_favorites(owner_id,campaign_id) "
                + "values (?,?::uuid)", OWNER, CAMPAIGN);
        jdbc.update("insert into application_job_market_links(owner_id,post_id,application_id) "
                + "values (?,?::uuid,?::uuid)", OWNER, JOB, APPLICATION);
    }

    @Test
    void testSchemaStartsWithoutFlywayAndSupportsSelectOnlyFixture() {
        assertThat(jdbc.queryForObject("select count(*) from job_market_company_read_models",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select to_regclass('public.flyway_schema_history')::text", String.class)).isNull();
    }
}
