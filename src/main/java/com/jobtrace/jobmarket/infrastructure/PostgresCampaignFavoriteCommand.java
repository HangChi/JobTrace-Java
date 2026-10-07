package com.jobtrace.jobmarket.infrastructure;

import com.jobtrace.jobmarket.application.CampaignFavoriteCommand;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresCampaignFavoriteCommand implements CampaignFavoriteCommand {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresCampaignFavoriteCommand(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean setFavorite(String ownerId, UUID campaignId, boolean favorite) {
        var parameters = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("campaignId", campaignId);
        String sql = favorite ? """
                with target as (
                  select id from job_market_campaigns where id = :campaignId
                ), inserted as (
                  insert into job_market_campaign_favorites(owner_id, campaign_id)
                  select :ownerId, id from target on conflict do nothing
                  returning campaign_id
                )
                select exists(select 1 from target)
                """ : """
                with target as (
                  select company_id from job_market_campaigns where id = :campaignId
                ), deleted as (
                  delete from job_market_campaign_favorites favorite
                  using target, job_market_campaigns campaign
                  where campaign.company_id = target.company_id
                    and favorite.campaign_id = campaign.id
                    and favorite.owner_id = :ownerId
                  returning favorite.campaign_id
                )
                select exists(select 1 from target)
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, parameters, Boolean.class));
    }
}
