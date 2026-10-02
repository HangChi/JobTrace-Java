package com.jobtrace.jobmarket.domain;
import java.util.List;
import java.util.Objects;
public record CampaignDetail(CampaignSummary summary, List<CampaignJob> jobs) {
    public CampaignDetail {
        Objects.requireNonNull(summary); jobs = List.copyOf(Objects.requireNonNull(jobs));
    }
}
