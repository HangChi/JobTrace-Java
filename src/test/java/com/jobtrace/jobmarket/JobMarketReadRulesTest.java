package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.jobtrace.jobmarket.domain.ApplyTargetPolicy;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.ApplyMode;
import com.jobtrace.jobmarket.domain.JobMarketCatalog.PostStatus;
import com.jobtrace.jobmarket.domain.MarketplaceQuery;
import org.junit.jupiter.api.Test;

class JobMarketReadRulesTest {

    @Test
    void defaultsAndClosedProjectionMatchLegacyRules() {
        var defaults = MarketplaceQuery.defaults();
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.limit()).isEqualTo(20);
        assertThat(defaults.includeClosed()).isFalse();
        assertThat(new MarketplaceQuery(null,null,null,PostStatus.CLOSED,null,null,1,20).includeClosed()).isTrue();
        assertThat(new MarketplaceQuery(null,null,null,null,null,true,1,20).includeClosed()).isTrue();
        assertThat(new MarketplaceQuery(null,null,null,null,null,false,1,20).includeClosed()).isFalse();
        assertThatThrownBy(() -> new MarketplaceQuery(null,null,null,null,null,null,0,20))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void onlyCanonicalHttpsTargetsAreActionable() {
        assertThat(ApplyTargetPolicy.canonicalHttps(" https://jobs.example.test/a/../b "))
                .isEqualTo("https://jobs.example.test/b");
        assertThat(ApplyTargetPolicy.canonicalHttps("http://jobs.example.test")).isNull();
        assertThat(ApplyTargetPolicy.canonicalHttps("javascript:alert(1)")).isNull();
        assertThat(ApplyTargetPolicy.campaignMode("https://jobs.example.test")).isEqualTo(ApplyMode.SINGLE);
        assertThat(ApplyTargetPolicy.campaignMode("file:///tmp/secret")).isEqualTo(ApplyMode.UNAVAILABLE);
    }
    @Test
    void unavailableReasonsPreserveEstablishedChineseCopy() {
        assertThat(ApplyTargetPolicy.unavailableReason(PostStatus.STALE,"https://jobs.example.test"))
                .isEqualTo("该岗位已失效");
        assertThat(ApplyTargetPolicy.unavailableReason(PostStatus.OPEN,null))
                .isEqualTo("来源未提供安全的官方投递地址");
        assertThat(ApplyTargetPolicy.unavailableReason(PostStatus.OPEN,"https://jobs.example.test")).isNull();
    }
}
