package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.interviews.application.InterviewReadQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InterviewDetailQueryIntegrationTest extends InterviewReadDatabaseTest {

    @Autowired
    private InterviewReadQuery query;

    @Test
    void childrenRetainSavedOrderAndRemainOwnerBound() {
        var detail = query.findDetail(OWNER, UUID.fromString(R102)).orElseThrow();
        assertThat(detail.questions()).extracting(item -> item.question())
                .containsExactly("SQL indexing", "Teamwork");
        assertThat(detail.actionItems()).extracting(item -> item.content())
                .containsExactly("Review indexes");
        assertThat(query.findDetail(OWNER, UUID.fromString(R201))).isEmpty();
        assertThat(query.findDetail(OTHER_OWNER, UUID.fromString(R102))).isEmpty();
    }

    @Test
    void deletedStageUsesSnapshotAndEmptyChildrenAreArrays() {
        jdbc.update("delete from application_stage_occurrences where id = ?::uuid",
                "00000000-0000-0000-0000-000000000301");
        var unlinked = query.findDetail(OWNER, UUID.fromString(R101)).orElseThrow();
        assertThat(unlinked.stage().value()).isEqualTo("interview_1");
        assertThat(unlinked.stageOccurrenceId()).isNull();
        assertThat(unlinked.linked()).isFalse();
        var empty = query.findDetail(OWNER, UUID.fromString(R103)).orElseThrow();
        assertThat(empty.questions()).isEmpty();
        assertThat(empty.actionItems()).isEmpty();
    }
}
