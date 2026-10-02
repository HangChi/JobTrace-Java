package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.application.ExportApplications;
import com.jobtrace.datatransfer.infrastructure.XlsxExportWriter;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LargeExportSafetyTest extends ExportDatabaseTest {

    @Autowired private ExportApplications applications;

    @Test
    void allAndFilteredDownloadsAreNotSilentlyTruncated() throws Exception {
        for (int index = 0; index < 1_200; index++) {
            jdbc.update("""
                    insert into applications(id,owner_id,company_name,position_name,
                      applied_date,type,status,latest_date,version,created_at,updated_at)
                    values (?::uuid, ?, '合成大文件公司', '岗位', '2026-09-01',
                      'spring_recruitment','submitted','2026-09-01',1,now(),now())
                    """, UUID.randomUUID().toString(), OWNER);
        }
        for (String scope : List.of("all", "filtered")) {
            var selection = ApplicationExportSelection.from(scope, "xlsx", List.of(),
                    scope.equals("filtered") ? "合成大文件" : null,
                    List.of(), List.of(), List.of(), List.of(), null, null);
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(
                    applications.execute(OWNER, selection).content()))) {
                assertThat(workbook.getSheetAt(0).getLastRowNum())
                        .isEqualTo(scope.equals("filtered") ? 1_200 : 1_202);
            }
        }
    }

    @Test
    void streamingWorkbookDoesNotTruncateOrLeaveScratchAfterSuccessOrFailure() throws Exception {
        Path scratch = Path.of(System.getProperty("java.io.tmpdir"), "poifiles");
        var before = scratchFiles(scratch);
        var rows = new ArrayList<ApplicationExportRow>();
        for (int index = 0; index < 1_200; index++) {
            rows.add(row(Integer.toString(index)));
        }
        var writer = new XlsxExportWriter();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(writer.write(rows)))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1_200);
        }
        assertThat(scratchFiles(scratch)).containsExactlyInAnyOrderElementsOf(before);
        var broken = new ArrayList<ApplicationExportRow>(rows.subList(0, 150));
        broken.add(null);
        assertThatThrownBy(() -> writer.write(broken)).isInstanceOf(NullPointerException.class);
        assertThat(scratchFiles(scratch)).containsExactlyInAnyOrderElementsOf(before);
    }

    private static List<String> scratchFiles(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static ApplicationExportRow row(String id) {
        return new ApplicationExportRow(id, "公司", "岗位", null, null,
                "2026-10-01", "春招", "已投递", "2026-10-01", "", "备注",
                "2026-10-01T00:00:00.000Z", "2026-10-01T00:00:00.000Z");
    }
}
