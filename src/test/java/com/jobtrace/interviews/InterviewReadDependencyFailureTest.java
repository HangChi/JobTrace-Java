package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewReadDependencyFailureTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allThreeReadsReturnSafeUnavailableProblems() throws Exception {
        jdbc.execute("drop table interview_reviews cascade");
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            for (String path : new String[] {"/api/interviews",
                    "/api/interviews/" + R102,
                    "/api/applications/" + APPLICATION_ID + "/detail"}) {
                String body = mockMvc.perform(get(path).with(asOwner(OWNER)))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("storage_unavailable"))
                        .andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain(OWNER, "relation", "SQL", "select", "Secret question");
            }
            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(OWNER, "Secret question", "SQL"));
        } finally {
            root.detachAppender(captured);
        }
    }
}
