package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.application.ExportFileWriter;
import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection.Format;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Portable CSV with the legacy BOM, quoting, and formula escaping rules. */
@Component
public class CsvExportWriter implements ExportFileWriter {

    @Override
    public Format format() {
        return Format.CSV;
    }

    @Override
    public byte[] write(List<ApplicationExportRow> rows) {
        var lines = new java.util.ArrayList<String>();
        lines.add(ApplicationExportRow.HEADERS.stream()
                .map(CsvExportWriter::csvCell).collect(Collectors.joining(",")));
        rows.forEach(row -> lines.add(row.cells().stream()
                .map(CsvExportWriter::safeFormula)
                .map(CsvExportWriter::csvCell).collect(Collectors.joining(","))));
        return ("\uFEFF" + String.join("\n", lines)).getBytes(StandardCharsets.UTF_8);
    }

    public static String safeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    public static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"") || value.contains("\r")
                || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
