package com.jobtrace.jobmarket.domain;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import java.util.Objects;

public record CampaignDetail(@JsonUnwrapped CampaignSummary summary, List<CampaignJob> jobs) {
    public CampaignDetail {
        Objects.requireNonNull(summary);
        jobs = List.copyOf(Objects.requireNonNull(jobs));
    }
}
