package com.jobtrace.jobmarket.domain;

import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import java.time.LocalDate;

public record MarketplaceQuery(String q, String company, String location, PostStatus status,
        LocalDate postedFrom, Boolean favorite, int page, int limit) {
    public MarketplaceQuery {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid marketplace pagination");
        }
        validateLength(q); validateLength(company); validateLength(location);
    }
    public static MarketplaceQuery defaults() {
        return new MarketplaceQuery(null, null, null, null, null, null, 1, 20);
    }
    public boolean includeClosed() {
        return Boolean.TRUE.equals(favorite) || status == PostStatus.CLOSED;
    }
    public int offset() {
        try { return Math.multiplyExact(page - 1, limit); }
        catch (ArithmeticException exception) {
            throw new IllegalArgumentException("marketplace page is too large", exception);
        }
    }
    private static void validateLength(String value) {
        if (value != null && value.length() > 100) {
            throw new IllegalArgumentException("marketplace text filter is too long");
        }
    }
}
