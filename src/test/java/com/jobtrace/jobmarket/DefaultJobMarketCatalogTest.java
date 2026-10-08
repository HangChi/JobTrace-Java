package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.jobmarket.application.DefaultJobMarketCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DefaultJobMarketCatalogTest {

    private final DefaultJobMarketCatalog catalog = new DefaultJobMarketCatalog(new ObjectMapper());

    @Test
    void preservesTheExistingDefaultDirectoryAndPaginatesIt() {
        var first = catalog.list(null, 1, 50);
        assertThat(first.total()).isEqualTo(1279);
        assertThat(first.items()).hasSize(50);
        assertThat(first.items().getFirst().companyName()).isEqualTo("腾讯");
        assertThat(first.items().getFirst().channel()).isEqualTo("automatic");
        assertThat(first.items().getFirst().adapter()).isEqualTo("china_bigtech");

        var last = catalog.list(null, 26, 50);
        assertThat(last.items()).hasSize(29);
        assertThat(last.total()).isEqualTo(1279);
    }

    @Test
    void searchMatchesNameIndustryAndChannelWithoutChangingTotals() {
        var page = catalog.list("腾讯", 1, 50);
        assertThat(page.total()).isGreaterThanOrEqualTo(1);
        assertThat(page.items()).anyMatch(item -> item.companyName().equals("腾讯"));
        assertThat(catalog.list("absent-company-identifier", 1, 50).items()).isEmpty();
    }

    @Test
    void rejectsOutOfRangeQueries() {
        assertThatThrownBy(() -> catalog.list("x".repeat(101), 1, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.list(null, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.list(null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
