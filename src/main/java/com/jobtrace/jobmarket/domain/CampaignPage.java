package com.jobtrace.jobmarket.domain;
import java.util.List;
import java.util.Objects;
public record CampaignPage(List<CampaignSummary> items, int page, int limit, int total) {
    public CampaignPage {
        items = List.copyOf(Objects.requireNonNull(items));
        if (page < 1 || limit < 1 || limit > 100 || total < 0) {
            throw new IllegalArgumentException("invalid campaign page metadata");
        }
    }
}
