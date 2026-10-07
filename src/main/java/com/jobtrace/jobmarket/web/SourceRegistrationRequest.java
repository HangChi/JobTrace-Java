package com.jobtrace.jobmarket.web;

import com.jobtrace.jobmarket.domain.SourceRegistration;
import java.util.List;
import java.util.UUID;

public record SourceRegistrationRequest(
        UUID companyId,
        String adapter,
        String externalKey,
        String baseUrl,
        List<String> allowedHosts,
        List<String> countryCodes,
        String accessBasis,
        Boolean isOfficial,
        Integer syncIntervalMinutes) {

    public SourceRegistration toDomain() {
        return new SourceRegistration(companyId, adapter, externalKey, baseUrl,
                allowedHosts, countryCodes == null ? List.of() : countryCodes,
                accessBasis, isOfficial == null || isOfficial,
                syncIntervalMinutes == null ? 360 : syncIntervalMinutes);
    }
}
