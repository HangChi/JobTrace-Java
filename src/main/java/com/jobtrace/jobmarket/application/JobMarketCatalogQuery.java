package com.jobtrace.jobmarket.application;

import java.util.List;

/** Curated marketplace companies as persisted by the shared remote database. */
public interface JobMarketCatalogQuery {
    Page list(String query, int page, int limit);

    record Item(String identityKey, String companyName, String adapter,
            String industry, String websiteUrl, String channel, String channelLabel,
            String channelHint) {}

    record Page(List<Item> items, int page, int limit, int total) {}
}
