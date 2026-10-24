package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.JobMarketCatalogQuery;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresJobMarketCatalogQuery implements JobMarketCatalogQuery {
    private static final String CATALOG = """
            select company.identity_key, company.canonical_name, company.industry,
              coalesce(directory.official_apply_url,company.website_url,
                source.base_url) as website_url,
              source.adapter::text as adapter,
              case when directory.id is not null
                then split_part(directory.campaign_key,':',2)
                when source.id is not null then 'automatic'
                else 'existing' end as channel,
              case when directory.id is not null then directory.name
                when source.id is not null then '自动同步：' || source.adapter::text
                else '已有公司' end as channel_label
            from job_market_companies company
            left join lateral (
              select id,campaign_key,name,official_apply_url
              from job_market_campaigns campaign
              where campaign.company_id=company.id
                and campaign.listing_kind='recruitment_directory'
                and campaign.status='open'
              order by campaign.updated_at desc,campaign.id limit 1
            ) directory on true
            left join lateral (
              select id,adapter,base_url from job_market_sources source
              where source.company_id=company.id and source.status<>'revoked'
              order by case when source.status='active' then 0 else 1 end,
                source.last_success_at desc nulls last,source.id limit 1
            ) source on true
            """;
    private static final String FILTER = """
            cast(:query as text) is null or position(lower(cast(:query as text)) in
              lower(concat_ws(' ',catalog.canonical_name,catalog.industry,
                catalog.adapter,catalog.channel_label)))>0
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresJobMarketCatalogQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page list(String query, int page, int limit) {
        String needle = query == null ? null : query.trim().toLowerCase(Locale.CHINA);
        if (needle != null && needle.length() > 100 || page < 1
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("目录查询参数无效。");
        }
        if (needle != null && needle.isEmpty()) {
            needle = null;
        }
        var parameters = new MapSqlParameterSource("query", needle)
                .addValue("limit", limit).addValue("offset", (long) (page - 1) * limit);
        String table = "(" + CATALOG + ") catalog";
        int total = jdbc.queryForObject("select count(*) from " + table
                + " where " + FILTER, parameters, Integer.class);
        List<Item> items = jdbc.query("""
                select catalog.identity_key,catalog.canonical_name,catalog.industry,
                  catalog.website_url,catalog.adapter,catalog.channel,catalog.channel_label
                from """ + table + " where " + FILTER
                + " order by catalog.canonical_name,catalog.identity_key"
                + " limit :limit offset :offset", parameters,
                (rs, row) -> new Item(rs.getString("identity_key"),
                        rs.getString("canonical_name"), rs.getString("adapter"),
                        rs.getString("industry"), rs.getString("website_url"),
                        rs.getString("channel"), rs.getString("channel_label"), null));
        return new Page(items, page, limit, total);
    }
}
