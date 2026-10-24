package com.jobtrace.reminders;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReminderOperationPerformanceTest extends ReminderOperationsDatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void representativeReadAndCreateMeetLocalP95() throws Exception {
        for (int index = 0; index < 5; index++) {
            read();
            create();
        }
        long[] readMs = new long[30];
        long[] createMs = new long[30];
        for (int index = 0; index < 30; index++) {
            readMs[index] = timed(this::read);
            createMs[index] = timed(this::create);
        }
        Arrays.sort(readMs);
        Arrays.sort(createMs);
        assertThat(readMs[28]).isLessThanOrEqualTo(500);
        assertThat(createMs[28]).isLessThanOrEqualTo(1000);
        System.out.printf("025 local p95 ms: read=%d create=%d; reminders=41%n",
                readMs[28], createMs[28]);
    }

    private void read() throws Exception {
        mvc.perform(get("/api/reminders").with(asOwner(OWNER)))
                .andExpect(status().isOk());
    }

    private void create() throws Exception {
        mvc.perform(post("/api/reminders").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","title":"Follow up",
                                 "eventAt":"2030-10-03T12:00:00Z",
                                 "notifyAt":"2030-10-03T10:00:00Z"}
                                """.formatted(APPLICATION_ID)))
                .andExpect(status().isCreated());
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
