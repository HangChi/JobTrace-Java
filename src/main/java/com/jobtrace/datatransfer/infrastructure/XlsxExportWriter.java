package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.application.ExportFileWriter;
import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection.Format;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/** Streaming XLSX writer; workbook closure removes POI scratch files. */
@Component
public class XlsxExportWriter implements ExportFileWriter {

    @Override
    public Format format() {
        return Format.XLSX;
    }

    @Override
    public byte[] write(List<ApplicationExportRow> rows) {
        try (var workbook = new SXSSFWorkbook(100);
                var output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            var sheet = workbook.createSheet("投递记录");
            var header = sheet.createRow(0);
            for (int index = 0; index < ApplicationExportRow.HEADERS.size(); index++) {
                header.createCell(index).setCellValue(ApplicationExportRow.HEADERS.get(index));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var item = rows.get(rowIndex);
                var sheetRow = sheet.createRow(rowIndex + 1);
                var values = item.cells();
                for (int column = 0; column < values.size(); column++) {
                    sheetRow.createCell(column).setCellValue(
                            CsvExportWriter.safeFormula(values.get(column)));
                }
                String safeLink = safeWebLink(item.jobUrl());
                if (safeLink != null) {
                    var link = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
                    link.setAddress(safeLink);
                    sheetRow.getCell(4).setHyperlink(link);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to create application export", exception);
        }
    }

    private static String safeWebLink(String value) {
        if (value == null) {
            return null;
        }
        try {
            var uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https")) ? uri.toString() : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
