package com.jobtrace.jobmarket.application;

import com.jobtrace.jobmarket.domain.CampaignDetail;
import com.jobtrace.jobmarket.domain.JobMarketNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetCampaignDetail {

    private final JobMarketReadQuery query;

    public GetCampaignDetail(JobMarketReadQuery query) {
        this.query = query;
    }

    public CampaignDetail execute(String ownerId, UUID campaignId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.findDetail(ownerId, Objects.requireNonNull(campaignId))
                .orElseThrow(JobMarketNotFoundException::new);
    }
}
