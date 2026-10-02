package com.jobtrace.jobmarket.application;

import com.jobtrace.jobmarket.domain.CampaignDetail;
import com.jobtrace.shared.web.Problem;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
                .orElseThrow(() -> new Problem(
                        "not_found", "没有找到这条招聘记录。", HttpStatus.NOT_FOUND));
    }
}
