package com.jobtrace.datatransfer.application;

import java.util.List;
import java.util.Map;

public interface ImportFileReader {

    Spreadsheet read(byte[] bytes, String filename);

    record Spreadsheet(List<String> columns, List<Map<String, String>> rows) {}
}
