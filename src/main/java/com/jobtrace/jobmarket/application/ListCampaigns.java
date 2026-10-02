package com.jobtrace.jobmarket.application;

import com.jobtrace.jobmarket.domain.CampaignPage;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListCampaigns {

    private final JobMarketReadQuery query;

    public ListCampaigns(JobMarketReadQuery query) {
        this.query = query;
    }

    public CampaignPage execute(String ownerId, MarketplaceQuery criteria) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.list(ownerId, Objects.requireNonNull(criteria));
    }
}
