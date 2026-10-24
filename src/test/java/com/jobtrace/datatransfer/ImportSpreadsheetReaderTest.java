package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.datatransfer.domain.ImportInputException;
import com.jobtrace.datatransfer.infrastructure.ImportSpreadsheetReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ImportSpreadsheetReaderTest {

    private final ImportSpreadsheetReader reader = new ImportSpreadsheetReader();

    @Test
    void readsUtf8CsvQuotesAndSkipsBlankRows() {
        String csv = "\uFEFF公司,岗位,投递日期\r\n\"示例,科技\",工程师,2026-08-13\r\n,,\r\n";
        var sheet = reader.read(csv.getBytes(StandardCharsets.UTF_8), "jobs.csv");
        assertThat(sheet.columns()).containsExactly("公司", "岗位", "投递日期");
        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().getFirst()).containsEntry("公司", "示例,科技");
    }

    @Test
    void readsOnlyFirstXlsxWorksheetWithoutEvaluatingFormulas() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("first");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("公司");
            header.createCell(1).setCellValue("岗位");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("示例科技");
            row.createCell(1).setCellFormula("1+1");
            workbook.createSheet("ignored").createRow(0).createCell(0).setCellValue("secret");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var result = reader.read(bytes, "jobs.xlsx");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst()).containsEntry("公司", "示例科技")
                .containsEntry("岗位", "");
        assertThat(result.columns()).doesNotContain("secret");
    }

    @Test
    void rejectsBadFilesAndMappingsBeforePersistence() {
        assertThatThrownBy(() -> reader.read("binary".getBytes(StandardCharsets.UTF_8), "a.exe"))
                .isInstanceOf(ImportInputException.class)
                .extracting("kind").isEqualTo(ImportInputException.Kind.UNSUPPORTED_FORMAT);
        assertThatThrownBy(() -> reader.read(new byte[] {'a', 0, 'b'}, "a.csv"))
                .isInstanceOf(ImportInputException.class);
        assertThatThrownBy(() -> reader.read("公司,公司\nx,y".getBytes(StandardCharsets.UTF_8),
                "a.csv")).isInstanceOf(ImportInputException.class);
        assertThatThrownBy(() -> reader.read("公司,岗位\n\"oops".getBytes(StandardCharsets.UTF_8),
                "a.csv")).isInstanceOf(ImportInputException.class);
        assertThatThrownBy(() -> reader.read(new byte[ImportSpreadsheetReader.MAX_BYTES + 1],
                "a.csv")).isInstanceOf(ImportInputException.class)
                .extracting("kind").isEqualTo(ImportInputException.Kind.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsInvalidUtf8AndCsvBoundaries() {
        assertThatThrownBy(() -> reader.read(new byte[] {(byte) 0xc3, 0x28}, "a.csv"))
                .isInstanceOf(ImportInputException.class);
        assertThatThrownBy(() -> reader.read(new byte[0], "a.csv"))
                .isInstanceOf(ImportInputException.class);
        assertThatThrownBy(() -> reader.read(" , , \nX,Y,Z".getBytes(StandardCharsets.UTF_8),
                "a.csv")).isInstanceOf(ImportInputException.class);
        String wide = "C,".repeat(101) + "\nvalue";
        assertThatThrownBy(() -> reader.read(wide.getBytes(StandardCharsets.UTF_8), "a.csv"))
                .isInstanceOf(ImportInputException.class);
        String overlong = "公司\n" + "示例\n".repeat(10_001);
        assertThatThrownBy(() -> reader.read(overlong.getBytes(StandardCharsets.UTF_8), "a.csv"))
                .isInstanceOf(ImportInputException.class);
    }

    @Test
    void handlesEscapedQuotesEmbeddedNewlinesAndShortRows() {
        var result = reader.read("公司,岗位,投递日期\n\"A\"\"B\nC\",Role,2026-08-13\n"
                .getBytes(StandardCharsets.UTF_8), "a.csv");
        assertThat(result.rows().getFirst()).containsEntry("公司", "A\"B\nC");
        var shortRow = reader.read("公司,岗位\nA\n".getBytes(StandardCharsets.UTF_8), "a.csv");
        assertThat(shortRow.rows().getFirst()).containsEntry("岗位", "");
        assertThatThrownBy(() -> reader.read("公司\n\"A\"junk".getBytes(StandardCharsets.UTF_8),
                "a.csv")).isInstanceOf(ImportInputException.class);
        assertThatThrownBy(() -> reader.read("公司\nA\"B".getBytes(StandardCharsets.UTF_8),
                "a.csv")).isInstanceOf(ImportInputException.class);
    }

    @Test
    void rejectsMalformedAndOversizedXlsxWorkbooks() throws Exception {
        assertThatThrownBy(() -> reader.read(new byte[] {0x50, 0x4b, 0}, "a.xlsx"))
                .isInstanceOf(ImportInputException.class);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.write(output);
            assertThatThrownBy(() -> reader.read(output.toByteArray(), "a.xlsx"))
                    .isInstanceOf(ImportInputException.class);
        }
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("first").createRow(1).createCell(0).setCellValue("orphan");
            workbook.write(output);
            assertThatThrownBy(() -> reader.read(output.toByteArray(), "a.xlsx"))
                    .isInstanceOf(ImportInputException.class);
        }
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("first");
            sheet.createRow(0).createCell(0).setCellValue("公司");
            sheet.createRow(10_001).createCell(0).setCellValue("large");
            workbook.write(output);
            assertThatThrownBy(() -> reader.read(output.toByteArray(), "a.xlsx"))
                    .isInstanceOf(ImportInputException.class)
                    .extracting("kind").isEqualTo(ImportInputException.Kind.PAYLOAD_TOO_LARGE);
        }
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("first").createRow(0).createCell(100).setCellValue("wide");
            workbook.write(output);
            assertThatThrownBy(() -> reader.read(output.toByteArray(), "a.xlsx"))
                    .isInstanceOf(ImportInputException.class)
                    .extracting("kind").isEqualTo(ImportInputException.Kind.PAYLOAD_TOO_LARGE);
        }
    }
}
