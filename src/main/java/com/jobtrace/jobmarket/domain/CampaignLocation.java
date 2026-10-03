package com.jobtrace.jobmarket.domain;

public record CampaignLocation(String name, boolean isRemote) {
    public CampaignLocation {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("location name must not be blank");
        }
    }
}
