package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.interviews.application.InterviewReadQuery;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InterviewListOwnerIsolationIntegrationTest extends InterviewReadDatabaseTest {

    @Autowired
    private InterviewReadQuery query;

    @Test
    void listTotalQuestionSearchAndPublicationStayWithinOwner() {
        var all = query.list(OWNER, criteria(null, null));
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.items()).extracting(item -> item.id())
                .containsExactly(R103, R102, R101).doesNotContain(R201);
        assertThat(query.list(OWNER, criteria("Secret question", null)).total()).isZero();
        assertThat(query.list(OWNER, criteria("SQL indexing", null)).items())
                .extracting(item -> item.id()).containsExactly(R102);
        assertThat(query.list(OWNER, criteria("Example Labs", null)).total()).isEqualTo(2);
        assertThat(query.list(OWNER, criteria("Designer", null)).items())
                .extracting(item -> item.id()).containsExactly(R103);
        assertThat(query.list(OWNER, criteria(null, List.of("anonymous"))).items())
                .extracting(item -> item.id()).containsExactly(R102);
        assertThat(query.list(OWNER, criteria(null, List.of("private"))).items())
                .extracting(item -> item.id()).containsExactly(R101);
        assertThat(query.list(OWNER, criteria(null, List.of("attributed"))).items())
                .extracting(item -> item.id()).containsExactly(R103);
        assertThat(query.list(OTHER_OWNER, criteria(null, null)).items())
                .extracting(item -> item.id()).containsExactly(R201);
    }

    private static InterviewListCriteria criteria(String text, List<String> publication) {
        return InterviewListCriteria.from(null, text, null, null, null,
                publication, null, null, null, null);
    }
}
