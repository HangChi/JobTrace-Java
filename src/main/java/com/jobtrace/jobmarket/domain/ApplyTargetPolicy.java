package com.jobtrace.jobmarket.domain;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.ApplyMode;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import java.net.URI;
public final class ApplyTargetPolicy {
    public static final String EXPIRED_REASON = "该岗位已失效";
    public static final String UNSAFE_REASON = "来源未提供安全的官方投递地址";
    private ApplyTargetPolicy() {}
    public static String canonicalHttps(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null) return null;
            return uri.toString();
        } catch (IllegalArgumentException exception) { return null; }
    }
    public static ApplyMode campaignMode(String value) {
        return canonicalHttps(value) == null ? ApplyMode.UNAVAILABLE : ApplyMode.SINGLE;
    }
    public static String unavailableReason(PostStatus status, String safeUrl) {
        if (status != PostStatus.OPEN) return EXPIRED_REASON;
        return canonicalHttps(safeUrl) == null ? UNSAFE_REASON : null;
    }
}
