package com.jobtrace.jobmarket.domain;
public record CampaignSource(String name, String url) {
    public CampaignSource {
        name = name == null || name.isBlank() ? "unknown" : name;
        url = ApplyTargetPolicy.canonicalHttps(url);
    }
}
