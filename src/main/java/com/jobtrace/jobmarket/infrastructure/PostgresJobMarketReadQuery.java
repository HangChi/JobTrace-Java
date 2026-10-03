package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.jobtrace.jobmarket.domain.ApplyTargetPolicy;
import com.jobtrace.jobmarket.domain.CampaignCompany;
import com.jobtrace.jobmarket.domain.CampaignDetail;
import com.jobtrace.jobmarket.domain.CampaignJob;
import com.jobtrace.jobmarket.domain.CampaignLocation;
import com.jobtrace.jobmarket.domain.CampaignPage;
import com.jobtrace.jobmarket.domain.CampaignSource;
import com.jobtrace.jobmarket.domain.CampaignSummary;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.ListingKind;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parameterized reads over the legacy-maintained job-market projection. */
@Repository
public class PostgresJobMarketReadQuery implements JobMarketReadQuery {

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresJobMarketReadQuery(
            NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public CampaignPage list(String ownerId, MarketplaceQuery query) {
        MapSqlParameterSource parameters = parameters(ownerId, query);
        int total = Optional.ofNullable(jdbc.queryForObject("""
                select count(*) from job_market_company_read_models model
                join job_market_companies company on company.id = model.company_id
                where model.include_closed = :includeClosed
                  and (cast(:q as text) is null or model.search_text like :qLike)
                  and (cast(:company as text) is null
                    or lower(company.canonical_name) like :companyLike)
                  and (cast(:location as text) is null or model.location_text like :locationLike)
                  and (cast(:status as text) is null or model.status = :status)
                  and (cast(:postedFrom as date) is null
                    or model.published_at::date >= cast(:postedFrom as date))
                  and (:favoriteOnly = false or exists (
                    select 1 from job_market_campaign_favorites favorite
                    join job_market_campaigns campaign on campaign.id = favorite.campaign_id
                    where campaign.company_id = model.company_id
                      and favorite.owner_id = :ownerId))
                """, parameters, Integer.class)).orElse(0);
        List<CampaignSummary> items = jdbc.query("""
                select model.representative_campaign_id, model.listing_kind,
                  company.id as company_id, company.canonical_name, company.company_type,
                  company.industry, model.recruitment_type, model.positions[1\\:50] as positions,
                  model.position_count, model.locations::text as locations, model.status,
                  model.primary_apply_url, model.source_name, model.source_url,
                  model.published_at, model.valid_through, model.last_confirmed_at,
                  exists (
                    select 1 from job_market_campaign_favorites favorite
                    join job_market_campaigns campaign on campaign.id = favorite.campaign_id
                    where campaign.company_id = model.company_id
                      and favorite.owner_id = :ownerId) as is_favorite
                from job_market_company_read_models model
                join job_market_companies company on company.id = model.company_id
                where model.include_closed = :includeClosed
                  and (cast(:q as text) is null or model.search_text like :qLike)
                  and (cast(:company as text) is null
                    or lower(company.canonical_name) like :companyLike)
                  and (cast(:location as text) is null or model.location_text like :locationLike)
                  and (cast(:status as text) is null or model.status = :status)
                  and (cast(:postedFrom as date) is null
                    or model.published_at::date >= cast(:postedFrom as date))
                  and (:favoriteOnly = false or exists (
                    select 1 from job_market_campaign_favorites favorite
                    join job_market_campaigns campaign on campaign.id = favorite.campaign_id
                    where campaign.company_id = model.company_id
                      and favorite.owner_id = :ownerId))
                order by model.published_at desc nulls last,
                  model.last_confirmed_at desc nulls last, model.company_id
                limit :limit offset :offset
                """, parameters, (rs, row) -> summary(rs));
        return new CampaignPage(items, query.page(), query.limit(), total);
    }

    @Override
    public Optional<CampaignDetail> findDetail(String ownerId, UUID campaignId) {
        var companyIds = jdbc.query("""
                select company_id from job_market_campaigns where id = :campaignId
                """, new MapSqlParameterSource("campaignId", campaignId),
                (rs, row) -> rs.getObject("company_id", UUID.class));
        if (companyIds.isEmpty()) {
            return Optional.empty();
        }
        UUID companyId = companyIds.getFirst();
        var summaryParameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("companyId", companyId);
        var summaries = jdbc.query("""
                select model.representative_campaign_id, model.listing_kind,
                  company.id as company_id, company.canonical_name, company.company_type,
                  company.industry, model.recruitment_type, model.positions,
                  model.position_count, model.locations::text as locations, model.status,
                  model.primary_apply_url, model.source_name, model.source_url,
                  model.published_at, model.valid_through, model.last_confirmed_at,
                  exists (
                    select 1 from job_market_campaign_favorites favorite
                    join job_market_campaigns campaign on campaign.id = favorite.campaign_id
                    where campaign.company_id = model.company_id
                      and favorite.owner_id = :ownerId) as is_favorite
                from job_market_company_read_models model
                join job_market_companies company on company.id = model.company_id
                where model.company_id = :companyId and model.include_closed = false
                """, summaryParameters, (rs, row) -> summary(rs));
        if (summaries.isEmpty()) {
            return Optional.empty();
        }
        CampaignSummary summary = summaries.getFirst();
        List<CampaignJob> jobs = summary.listingKind() == ListingKind.RECRUITMENT_DIRECTORY
                ? List.of() : jobs(ownerId, companyId);
        return Optional.of(new CampaignDetail(summary, jobs));
    }

    private List<CampaignJob> jobs(String ownerId, UUID companyId) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("companyId", companyId);
        return jdbc.query("""
                select post.id, post.title, post.status, post.primary_apply_url,
                  post.published_at, post.valid_through, source.adapter as source_name,
                  source.base_url as source_url, link.application_id,
                  coalesce((select jsonb_agg(jsonb_build_object(
                    'name', location_values.display_name,
                    'isRemote', location_values.is_remote)
                    order by location_values.display_name, location_values.is_remote)
                    from (select distinct location.display_name, location.is_remote
                      from job_market_post_locations relation
                      join job_market_locations location on location.id = relation.location_id
                      where relation.post_id = post.id) location_values), '[]')::text as locations
                from job_market_posts post
                join job_market_companies company on company.id = post.company_id
                left join lateral (
                  select job_source.adapter, job_source.base_url
                  from job_market_source_records record
                  join job_market_sources job_source on job_source.id = record.source_id
                  where record.post_id = post.id and job_source.status = 'active'
                  order by job_source.is_official desc, record.last_seen_at desc
                  limit 1) source on true
                left join application_job_market_links link
                  on link.post_id = post.id and link.owner_id = :ownerId
                where post.company_id = :companyId and source.adapter is not null
                  and post.status <> 'closed'
                order by post.published_at desc nulls last, post.title, post.id
                """, parameters, (rs, row) -> new CampaignJob(
                rs.getObject("id", UUID.class), rs.getString("title"),
                locations(rs.getString("locations")),
                PostStatus.fromWire(rs.getString("status")), rs.getString("primary_apply_url"),
                null, timestamp(rs.getTimestamp("published_at")),
                timestamp(rs.getTimestamp("valid_through")), rs.getString("source_name"),
                rs.getString("source_url"), rs.getObject("application_id", UUID.class)));
    }

    private CampaignSummary summary(ResultSet rs) throws SQLException {
        String applyUrl = rs.getString("primary_apply_url");
        return new CampaignSummary(
                rs.getObject("representative_campaign_id", UUID.class),
                ListingKind.fromWire(rs.getString("listing_kind")),
                new CampaignCompany(rs.getObject("company_id", UUID.class),
                        rs.getString("canonical_name"), rs.getString("company_type"),
                        rs.getString("industry")),
                null, rs.getString("recruitment_type"), null, strings(rs.getArray("positions")),
                rs.getInt("position_count"), locations(rs.getString("locations")),
                PostStatus.fromWire(rs.getString("status")),
                ApplyTargetPolicy.campaignMode(applyUrl), applyUrl,
                new CampaignSource(rs.getString("source_name"), rs.getString("source_url")),
                timestamp(rs.getTimestamp("published_at")),
                timestamp(rs.getTimestamp("valid_through")),
                timestamp(rs.getTimestamp("last_confirmed_at")), rs.getBoolean("is_favorite"));
    }

    private MapSqlParameterSource parameters(String ownerId, MarketplaceQuery query) {
        return new MapSqlParameterSource()
                .addValue("ownerId", ownerId).addValue("includeClosed", query.includeClosed())
                .addValue("q", lower(query.q())).addValue("qLike", like(query.q()))
                .addValue("company", lower(query.company()))
                .addValue("companyLike", like(query.company()))
                .addValue("location", lower(query.location()))
                .addValue("locationLike", like(query.location()))
                .addValue("status", query.status() == null ? null : query.status().wire())
                .addValue("postedFrom", query.postedFrom())
                .addValue("favoriteOnly", Boolean.TRUE.equals(query.favorite()))
                .addValue("limit", query.limit()).addValue("offset", query.offset());
    }

    private List<CampaignLocation> locations(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            List<CampaignLocation> values = new ArrayList<>();
            for (JsonNode item : root) {
                values.add(new CampaignLocation(item.get("name").asText(),
                        item.get("isRemote").asBoolean()));
            }
            return values;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid location projection", exception);
        }
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] raw = (Object[]) array.getArray();
        List<String> values = new ArrayList<>(raw.length);
        for (Object value : raw) {
            values.add(String.valueOf(value));
        }
        return values;
    }

    private static String timestamp(Timestamp value) {
        return value == null ? null : UTC_MILLIS.format(value.toInstant());
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String like(String value) {
        String lowered = lower(value);
        return lowered == null ? null : "%" + lowered + "%";
    }
}
