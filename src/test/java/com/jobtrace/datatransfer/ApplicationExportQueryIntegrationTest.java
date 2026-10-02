package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.infrastructure.PostgresExportReadQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationExportQueryIntegrationTest extends ExportDatabaseTest {

    @Autowired
    private PostgresExportReadQuery query;

    @Test
    void allAndFilteredExportsAreOwnedOrderedAndComplete() {
        var all = ApplicationExportSelection.from("all", "csv", List.of(), null,
                List.of(), List.of(), List.of(), List.of(), null, null);
        var rows = query.applications(OWNER, all);
        assertThat(rows).extracting(row -> row.id()).containsExactly(A101, A102);
        assertThat(rows.getFirst().stageHistory())
                .isEqualTo("screening/简历筛选 + 2026-09-30；interview_1/一面/AI面 + 2026-10-01");
        assertThat(rows.getFirst().typeLabel()).isEqualTo("春招");
        assertThat(rows.getFirst().notes()).isEqualTo("=2+3\n第二行");
        assertThat(rows).extracting(row -> row.company()).doesNotContain("不可见公司");

        var filtered = ApplicationExportSelection.from("filtered", "csv", List.of(),
                "示例", List.of("submitted"), List.of("spring_recruitment"),
                List.of("interview_1"), List.of("上海"), "2026-09-30", "2026-09-30");
        assertThat(query.applications(OWNER, filtered)).extracting(row -> row.id())
                .containsExactly(A101);
        assertThat(query.applications(OTHER_OWNER, filtered)).isEmpty();
    }
}
