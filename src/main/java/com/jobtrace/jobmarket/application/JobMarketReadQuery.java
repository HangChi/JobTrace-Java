package com.jobtrace.jobmarket.application;
import com.jobtrace.jobmarket.domain.CampaignDetail;
import com.jobtrace.jobmarket.domain.CampaignPage;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import java.util.Optional;
import java.util.UUID;
/** Owner is supplied only by the verified bridge; this port exposes reads only. */
public interface JobMarketReadQuery {
    CampaignPage list(String ownerId, MarketplaceQuery query);
    Optional<CampaignDetail> findDetail(String ownerId, UUID campaignId);
}
