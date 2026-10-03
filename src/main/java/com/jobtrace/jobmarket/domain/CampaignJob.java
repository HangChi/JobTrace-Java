package com.jobtrace.jobmarket.domain;

import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CampaignJob(UUID id, String title, List<CampaignLocation> locations, PostStatus status,
        String applyUrl, String applyUnavailableReason, String publishedAt, String validThrough,
        String sourceName, String sourceUrl, UUID alreadyTrackedApplicationId) {
    public CampaignJob {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("job title must not be blank");
        }
        locations = List.copyOf(Objects.requireNonNull(locations));
        applyUrl = ApplyTargetPolicy.canonicalHttps(applyUrl);
        applyUnavailableReason = ApplyTargetPolicy.unavailableReason(status, applyUrl);
        sourceName = sourceName == null || sourceName.isBlank() ? "unknown" : sourceName;
        sourceUrl = ApplyTargetPolicy.canonicalHttps(sourceUrl);
    }
}
