package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.datatransfer.application.ConfirmImport;
import com.jobtrace.datatransfer.application.ImportStore;
import com.jobtrace.datatransfer.application.PreviewImport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ImportPerformanceTest extends ImportDatabaseTest {

    @Autowired PreviewImport preview;
    @Autowired ConfirmImport confirm;

    @Test
    void representativeSingleRowPreviewAndConfirmationMeetLocalBudget() {
        var previews = new ArrayList<Long>();
        var confirmations = new ArrayList<Long>();
        for (int sample = 0; sample < 35; sample++) {
            byte[] file = ("公司,岗位,投递日期\nPerf " + sample
                    + ",Engineer,2026-08-13\n").getBytes(StandardCharsets.UTF_8);
            long started = System.nanoTime();
            var batch = preview.execute(OWNER, "perf.csv", file, null, null);
            long previewMs = (System.nanoTime() - started) / 1_000_000;
            started = System.nanoTime();
            var result = confirm.execute(OWNER, batch.id(),
                    java.util.List.of(new ImportStore.Decision(2, "import")));
            long confirmationMs = (System.nanoTime() - started) / 1_000_000;
            assertThat(result.created()).isEqualTo(1);
            if (sample >= 5) {
                previews.add(previewMs);
                confirmations.add(confirmationMs);
            }
        }
        long previewP95 = p95(previews);
        long confirmationP95 = p95(confirmations);
        System.out.printf("026 local p95 ms: preview=%d confirm=%d; samples=%d%n",
                previewP95, confirmationP95, previews.size());
        assertThat(previewP95).isLessThanOrEqualTo(500);
        assertThat(confirmationP95).isLessThanOrEqualTo(1_000);
    }

    private long p95(ArrayList<Long> samples) {
        Collections.sort(samples);
        return samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
    }
}
