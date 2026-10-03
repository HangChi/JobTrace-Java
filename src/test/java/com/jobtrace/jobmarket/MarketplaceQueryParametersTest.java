package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import com.jobtrace.jobmarket.web.MarketplaceQueryParameters;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class MarketplaceQueryParametersTest {

    @Test
    void normalizesTextAndPreservesMultilingualAndMixedCaseValues() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("q", "  Java工程师 Acme  ");
        parameters.add("company", "  Example LABS ");
        parameters.add("location", "  上海 Remote ");
        parameters.add("status", "open");
        parameters.add("postedFrom", "2026-09-02");
        parameters.add("favorite", "true");
        parameters.add("page", "2");
        parameters.add("limit", "100");

        var query = MarketplaceQueryParameters.from(parameters);

        assertThat(query.q()).isEqualTo("Java工程师 Acme");
        assertThat(query.company()).isEqualTo("Example LABS");
        assertThat(query.location()).isEqualTo("上海 Remote");
        assertThat(query.status()).isEqualTo(PostStatus.OPEN);
        assertThat(query.postedFrom()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(query.favorite()).isTrue();
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.limit()).isEqualTo(100);
        assertThat(query.includeClosed()).isTrue();
    }

    @Test
    void treatsBlankTextAndFavoriteFalseAsOmittedSelection() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("q", " \t ");
        parameters.add("favorite", "false");

        var query = MarketplaceQueryParameters.from(parameters);

        assertThat(query.q()).isNull();
        assertThat(query.favorite()).isFalse();
        assertThat(query.includeClosed()).isFalse();
    }

    @Test
    void acceptsOneHundredCharactersAndRejectsLongerText() {
        var accepted = new LinkedMultiValueMap<String, String>();
        accepted.add("q", "x".repeat(100));
        assertThat(MarketplaceQueryParameters.from(accepted).q()).hasSize(100);

        var rejected = new LinkedMultiValueMap<String, String>();
        rejected.add("q", "x".repeat(101));
        assertThatThrownBy(() -> MarketplaceQueryParameters.from(rejected))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonCanonicalValuesAndOutOfRangePagination() {
        assertInvalid("status", "OPEN");
        assertInvalid("favorite", "TRUE");
        assertInvalid("postedFrom", "2026-9-2");
        assertInvalid("page", "0");
        assertInvalid("page", "2147483648");
        assertInvalid("limit", "101");
        assertInvalid("limit", "1.5");
    }

    private static void assertInvalid(String name, String value) {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add(name, value);
        assertThatThrownBy(() -> MarketplaceQueryParameters.from(parameters))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
