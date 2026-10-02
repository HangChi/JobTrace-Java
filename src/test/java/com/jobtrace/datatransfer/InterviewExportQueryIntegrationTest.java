package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import com.jobtrace.datatransfer.infrastructure.PostgresExportReadQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SpringBootTest
class InterviewExportQueryIntegrationTest extends ExportDatabaseTest {

    @Autowired private PostgresExportReadQuery query;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void joinsOnlyOwnedReviewsAndPreservesSelectedOrder() {
        var selected = InterviewExportSelection.from(List.of(R302, R401, R301, R302));
        var documents = query.interviews(OWNER, selected);
        assertThat(documents).extracting(document -> document.id()).containsExactly(R302, R301);
        assertThat(documents.get(0).questions().getFirst().text()).isEqualTo("第二篇复盘");
        assertThat(documents.get(1).stage()).isEqualTo("interview_1");
        assertThat(query.interviews(OTHER_OWNER, selected)).extracting(document -> document.id())
                .containsExactly(R401);
    }

    @Test
    void ignoresForeignStageAndReadsOwnedChildrenInThreeQueries() {
        jdbc.update("update interview_reviews set stage_occurrence_id = "
                + "'00000000-0000-0000-0000-000000000503' where id = ?::uuid", R301);
        jdbc.update("update interview_questions set original_answer = '回答', self_rating = 4 "
                + "where interview_review_id = ?::uuid", R301);
        jdbc.update("insert into interview_action_items "
                + "(id, interview_review_id, sort_order, content, completed) values "
                + "('00000000-0000-0000-0000-000000000701', ?::uuid, 0, '行动', false)", R301);
        var documents = query.interviews(OWNER, InterviewExportSelection.from(List.of(R301)));
        assertThat(documents.getFirst().stage()).isEqualTo("interview_1");
        assertThat(documents.getFirst().questions().getFirst().originalAnswer()).isEqualTo("回答");
        assertThat(documents.getFirst().actionCount()).isEqualTo(1);
    }

    @Test
    void batchReadUsesExactlyThreeSqlQueriesForAllOwnedSelections() {
        var countingJdbc = spy(namedJdbc);
        var counted = new PostgresExportReadQuery(countingJdbc);
        assertThat(counted.interviews(OWNER, InterviewExportSelection.from(List.of(R301, R302))))
                .hasSize(2);
        verify(countingJdbc, times(1)).query(anyString(), any(SqlParameterSource.class),
                any(RowMapper.class));
        verify(countingJdbc, times(2)).query(anyString(), any(SqlParameterSource.class),
                any(RowCallbackHandler.class));
    }
}
