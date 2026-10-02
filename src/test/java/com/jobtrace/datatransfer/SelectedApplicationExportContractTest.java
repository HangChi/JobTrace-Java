package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.infrastructure.CsvExportWriter;
import com.jobtrace.datatransfer.infrastructure.XlsxExportWriter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.WorkbookFactory;

class SelectedApplicationExportContractTest {

    @Test
    void selectedRequiresOneToOneHundredIdsAndIgnoresFilters() {
        assertThatThrownBy(() -> selected(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selected(Collections.nCopies(101, ExportDatabaseTest.A101)))
                .isInstanceOf(IllegalArgumentException.class);
        var selection = selected(List.of(ExportDatabaseTest.A101, ExportDatabaseTest.A101));
        assertThat(selection.scope()).isEqualTo(ApplicationExportSelection.Scope.SELECTED);
        assertThat(selection.ids()).hasSize(2);
        assertThat(selected(Collections.nCopies(100, ExportDatabaseTest.A101)).ids()).hasSize(100);
    }

    @Test
    void selectedUsesTheSameCsvAndWorkbookColumns() throws Exception {
        var row = new ApplicationExportRow(ExportDatabaseTest.A101, "公司", "岗位",
                null, null, "2026-10-01", "春招", "已投递", "2026-10-01", "", null,
                "2026-10-01T00:00:00.000Z", "2026-10-01T00:00:00.000Z");
        String csv = new String(new CsvExportWriter().write(List.of(row)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF" + String.join(",", ApplicationExportRow.HEADERS));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(
                new XlsxExportWriter().write(List.of(row))))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("公司");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("公司");
        }
    }

    private static ApplicationExportSelection selected(List<String> ids) {
        return ApplicationExportSelection.from("selected", "csv", ids, "no-match",
                List.of("refused"), List.of(), List.of(), List.of(), null, null);
    }
}
