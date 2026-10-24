package com.jobtrace.jobmarket.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CandidateReviewStore {
    CandidateList sources(String status);

    CandidateList companies(String status);

    Map<String, Object> reviewSource(UUID id, String action);

    Map<String, Object> reviewCompany(UUID id, String action, CompanyOverrides overrides);

    record CandidateList(List<Map<String, Object>> items, Map<String, Integer> summary) {}

    record CompanyOverrides(String companyName, String companyType, String industry) {}
}
