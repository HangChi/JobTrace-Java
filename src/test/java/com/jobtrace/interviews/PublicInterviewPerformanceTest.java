package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicInterviewPerformanceTest extends PublicInterviewDatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void representativePublicReadsAndLikeWritesMeetLocalP95() throws Exception {
        for (int index = 0; index < 5; index++) {
            feed();
            detail();
            like();
        }
        long[] feedMs = new long[30];
        long[] detailMs = new long[30];
        long[] likeMs = new long[30];
        for (int index = 0; index < 30; index++) {
            feedMs[index] = timed(this::feed);
            detailMs[index] = timed(this::detail);
            likeMs[index] = timed(this::like);
        }
        Arrays.sort(feedMs);
        Arrays.sort(detailMs);
        Arrays.sort(likeMs);
        assertThat(feedMs[28]).isLessThanOrEqualTo(500);
        assertThat(detailMs[28]).isLessThanOrEqualTo(500);
        assertThat(likeMs[28]).isLessThanOrEqualTo(1000);
        System.out.printf("024 local p95 ms: feed=%d detail=%d like=%d%n",
                feedMs[28], detailMs[28], likeMs[28]);
    }

    private void feed() throws Exception {
        mvc.perform(get("/api/interviews/public").with(asOwner(OTHER_OWNER)))
                .andExpect(status().isOk());
    }

    private void detail() throws Exception {
        mvc.perform(get("/api/interviews/public/{id}", R102)
                .with(asOwner(OTHER_OWNER))).andExpect(status().isOk());
    }

    private void like() throws Exception {
        mvc.perform(post("/api/interviews/public/{id}/like", R102)
                .with(asOwner(OTHER_OWNER)).with(csrf())).andExpect(status().isOk());
    }

    private static long timed(CheckedAction action) throws Exception {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
