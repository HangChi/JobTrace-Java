package com.jobtrace.jobmarket.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ManageJobMarketCandidates {
    private final CandidateReviewStore store;

    public ManageJobMarketCandidates(CandidateReviewStore store) {
        this.store = store;
    }

    public CandidateReviewStore.CandidateList sources(String status) {
        validateStatus(status, Set.of("unrecognized", "pending", "approved", "ignored"));
        return store.sources(status);
    }

    public CandidateReviewStore.CandidateList companies(String status) {
        validateStatus(status, Set.of("pending", "approved", "ignored"));
        return store.companies(status);
    }

    public Map<String, Object> reviewSource(UUID id, String action) {
        validateAction(action);
        return store.reviewSource(id, action);
    }

    public Map<String, Object> reviewCompany(UUID id, String action,
            CandidateReviewStore.CompanyOverrides overrides) {
        validateAction(action);
        if (overrides != null) {
            String name = overrides.companyName();
            String type = overrides.companyType();
            String industry = overrides.industry();
            if (name != null && (name.trim().length() < 2 || name.trim().length() > 60)
                    || type != null && !Set.of("企业", "民营企业", "上市公司", "国有企业",
                            "中央企业", "事业单位", "外企").contains(type)
                    || industry != null && (industry.trim().length() < 2
                            || industry.trim().length() > 60)) {
                throw new IllegalArgumentException("公司审核信息无效。");
            }
        }
        return store.reviewCompany(id, action, overrides);
    }

    private static void validateAction(String action) {
        if (!"approve".equals(action) && !"ignore".equals(action)) {
            throw new IllegalArgumentException("候选审核操作无效。");
        }
    }

    private static void validateStatus(String status, Set<String> allowed) {
        if (status != null && !allowed.contains(status)) {
            throw new IllegalArgumentException("候选状态无效。");
        }
    }
}
