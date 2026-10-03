package com.jobtrace.jobmarket.domain;

import com.jobtrace.jobmarket.domain.JobMarketCatalog.ApplyMode;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.ListingKind;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CampaignSummary(UUID id, ListingKind listingKind, CampaignCompany company,
        String campaignName, String recruitmentType, String batchLabel, List<String> positions,
        int positionCount, List<CampaignLocation> locations, PostStatus status, ApplyMode applyMode,
        String primaryApplyUrl, CampaignSource source, String publishedAt, String validThrough,
        String lastConfirmedAt, boolean isFavorite) {
    public CampaignSummary {
        Objects.requireNonNull(id);
        Objects.requireNonNull(listingKind);
        Objects.requireNonNull(company);
        positions = List.copyOf(Objects.requireNonNull(positions));
        locations = List.copyOf(Objects.requireNonNull(locations));
        Objects.requireNonNull(status);
        Objects.requireNonNull(applyMode);
        Objects.requireNonNull(source);
        primaryApplyUrl = ApplyTargetPolicy.canonicalHttps(primaryApplyUrl);
        if (positionCount < 0 || positions.size() > positionCount) {
            throw new IllegalArgumentException("invalid position projection");
        }
    }
}
