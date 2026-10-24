package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.application.ImportFileReader;
import com.jobtrace.datatransfer.domain.ImportInputException;
import com.jobtrace.datatransfer.domain.ImportInputException.Kind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/** Bounded CSV/first-sheet XLSX parser. Formulas are never evaluated. */
@Component
public class ImportSpreadsheetReader implements ImportFileReader {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_ROWS = 10_000;
    public static final int MAX_COLUMNS = 100;
    public static final int MAX_CELLS = 200_000;

    @Override
    public Spreadsheet read(byte[] bytes, String filename) {
        if (bytes == null || bytes.length > MAX_BYTES) {
            throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE, "文件不得超过 5MB。");
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            for (byte value : bytes) {
                if (value == 0) {
                    throw new ImportInputException(Kind.UNSUPPORTED_FORMAT,
                            "CSV 文件包含无效的二进制内容。");
                }
            }
            try {
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                return fromRecords(csvRecords(text));
            } catch (CharacterCodingException exception) {
                throw new ImportInputException(Kind.VALIDATION, "CSV 文件必须使用 UTF-8 编码。");
            }
        }
        if (lower.endsWith(".xlsx")) {
            if (bytes.length < 2 || bytes[0] != 0x50 || bytes[1] != 0x4b) {
                throw new ImportInputException(Kind.UNSUPPORTED_FORMAT, "文件内容不是有效的 XLSX。");
            }
            return xlsx(bytes);
        }
        throw new ImportInputException(Kind.UNSUPPORTED_FORMAT, "仅支持 CSV 或 XLSX。");
    }

    private Spreadsheet xlsx(byte[] bytes) {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ImportInputException(Kind.VALIDATION, "文件中没有工作表。");
            }
            var sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE, "数据不得超过 10,000 行。");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new ImportInputException(Kind.VALIDATION, "文件中没有表头。");
            }
            int columnCount = Math.max(0, header.getLastCellNum());
            if (columnCount > MAX_COLUMNS || (long) columnCount * sheet.getLastRowNum() > MAX_CELLS) {
                throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE,
                        "表格列数或单元格数量超出允许范围。");
            }
            var formatter = new DataFormatter(Locale.ROOT);
            var records = new ArrayList<List<String>>();
            records.add(cellValues(header, columnCount, formatter));
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row != null && row.getLastCellNum() > MAX_COLUMNS) {
                    throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE, "表格列数超出允许范围。");
                }
                records.add(cellValues(row, columnCount, formatter));
            }
            return fromRecords(records);
        } catch (ImportInputException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ImportInputException(Kind.VALIDATION, "无法读取 XLSX 文件。");
        }
    }

    private List<String> cellValues(Row row, int count, DataFormatter formatter) {
        var values = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            Cell cell = row == null ? null : row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(cell == null || cell.getCellType() == CellType.FORMULA
                    ? "" : formatter.formatCellValue(cell));
        }
        return values;
    }

    private Spreadsheet fromRecords(List<List<String>> records) {
        if (records.isEmpty()) {
            throw new ImportInputException(Kind.VALIDATION, "文件中没有表头。");
        }
        List<String> header = records.getFirst().stream().map(String::trim).toList();
        if (header.stream().noneMatch(value -> !value.isEmpty())) {
            throw new ImportInputException(Kind.VALIDATION, "文件中没有表头。");
        }
        if (header.size() > MAX_COLUMNS || (long) header.size() * (records.size() - 1) > MAX_CELLS) {
            throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE,
                    "表格列数或单元格数量超出允许范围。");
        }
        var nonblankHeaders = header.stream().filter(value -> !value.isEmpty()).toList();
        if (new LinkedHashSet<>(nonblankHeaders).size() != nonblankHeaders.size()) {
            throw new ImportInputException(Kind.VALIDATION, "文件表头不能重复。");
        }
        var rows = new ArrayList<Map<String, String>>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> record = records.get(rowIndex);
            if (record.size() > MAX_COLUMNS) {
                throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE, "表格列数超出允许范围。");
            }
            var values = new LinkedHashMap<String, String>();
            boolean any = false;
            for (int column = 0; column < header.size(); column++) {
                String value = column < record.size() ? record.get(column) : "";
                if (!value.isEmpty()) {
                    any = true;
                }
                if (!header.get(column).isEmpty()) {
                    values.put(header.get(column), value);
                }
            }
            if (any) {
                rows.add(Collections.unmodifiableMap(values));
                if (rows.size() > MAX_ROWS) {
                    throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE,
                            "数据不得超过 10,000 行。");
                }
            }
        }
        return new Spreadsheet(List.copyOf(nonblankHeaders), List.copyOf(rows));
    }

    private List<List<String>> csvRecords(String text) {
        var records = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(value);
                }
                continue;
            }
            if (value == '"') {
                if (field.length() != 0 || closedQuote) {
                    throw new ImportInputException(Kind.VALIDATION, "CSV 引号格式无效。");
                }
                quoted = true;
            } else if (value == ',' || value == '\n' || value == '\r') {
                current.add(field.toString());
                field.setLength(0);
                closedQuote = false;
                if (value != ',') {
                    records.add(List.copyOf(current));
                    current.clear();
                    if (value == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                        index++;
                    }
                    if (records.size() > MAX_ROWS + 2) {
                        throw new ImportInputException(Kind.PAYLOAD_TOO_LARGE,
                                "数据不得超过 10,000 行。");
                    }
                }
            } else if (closedQuote) {
                throw new ImportInputException(Kind.VALIDATION, "CSV 引号格式无效。");
            } else {
                field.append(value);
            }
        }
        if (quoted) {
            throw new ImportInputException(Kind.VALIDATION, "CSV 引号格式无效。");
        }
        if (!current.isEmpty() || field.length() > 0 || closedQuote) {
            current.add(field.toString());
            records.add(List.copyOf(current));
        }
        if (!records.isEmpty() && !records.getFirst().isEmpty()) {
            var first = new ArrayList<>(records.getFirst());
            first.set(0, first.getFirst().replaceFirst("^\\uFEFF", ""));
            records.set(0, List.copyOf(first));
        }
        return records;
    }
}
