package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.jobmarket.application.JobMarketCatalogQuery;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobMarketCatalogPerformanceTest extends JobMarketReadDatabaseTest {
    @Autowired
    private JobMarketCatalogQuery catalog;

    @Test
    void thousandCompanyRemoteCatalogMeetsReadBudget() {
        jdbc.update("""
                insert into job_market_companies(id,canonical_name,normalized_name,identity_key)
                select gen_random_uuid(),'Catalog Company ' || n,
                  'catalog company ' || n,'perf:catalog:' || n
                from generate_series(1,1200) n
                """);
        jdbc.execute("analyze job_market_companies; analyze job_market_campaigns; "
                + "analyze job_market_sources");
        for (int index = 0; index < 5; index++) {
            catalog.list(null, 1, 50);
        }
        long[] samples = new long[30];
        for (int index = 0; index < samples.length; index++) {
            long start = System.nanoTime();
            assertThat(catalog.list(null, 1, 50).total()).isEqualTo(1202);
            samples[index] = (System.nanoTime() - start) / 1_000_000;
        }
        Arrays.sort(samples);
        assertThat(samples[28]).isLessThanOrEqualTo(500);
        System.out.printf("027 shared catalog read p95 ms: %d; companies=1202%n",
                samples[28]);
    }
}
