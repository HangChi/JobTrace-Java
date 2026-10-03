package com.jobtrace.jobmarket.domain;

import java.util.Objects;
import java.util.UUID;

public record CampaignCompany(UUID id, String name, String type, String industry) {
    public CampaignCompany {
        Objects.requireNonNull(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("company name must not be blank");
        }
    }
}
