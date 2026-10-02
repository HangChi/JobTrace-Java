package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.infrastructure.CsvExportWriter;
import com.jobtrace.datatransfer.infrastructure.XlsxExportWriter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class ApplicationExportFileContractTest {

    private static final ApplicationExportRow ROW = new ApplicationExportRow(
            "00000000-0000-0000-0000-000000000101", "示例,科技", "后端\"工程师", "上海",
            "https://example.test/jobs/101", "2026-09-30", "春招", "已投递", "2026-10-01",
            "interview_1/一面/AI面 + 2026-10-01", "=2+3\n第二行",
            "2026-09-30T01:02:03.000Z", "2026-10-01T01:02:03.000Z");

    @Test
    void csvUsesLegacyColumnsBomEscapingAndQuotes() {
        String csv = new String(new CsvExportWriter().write(List.of(ROW)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF" + String.join(",", ApplicationExportRow.HEADERS));
        assertThat(csv).contains("\"示例,科技\"", "\"后端\"\"工程师\"",
                "\"'=2+3\n第二行\"");
    }

    @Test
    void workbookStoresLiteralTextAndOnlySafeHyperlinks() throws Exception {
        var unsafe = new ApplicationExportRow("2", "Unsafe", "Designer", null,
                "javascript:alert(1)", "2026-09-20", "社招", "Offer", "2026-09-20",
                "", null, "2026-09-20T01:00:00.000Z", "2026-09-20T01:00:00.000Z");
        byte[] bytes = new XlsxExportWriter().write(List.of(ROW, unsafe));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("投递记录");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(sheet.getRow(1).getCell(10).getStringCellValue())
                    .isEqualTo("'=2+3\n第二行");
            assertThat(sheet.getRow(1).getCell(4).getHyperlink().getAddress())
                    .isEqualTo("https://example.test/jobs/101");
            assertThat(sheet.getRow(2).getCell(4).getHyperlink()).isNull();
        }
    }
}
