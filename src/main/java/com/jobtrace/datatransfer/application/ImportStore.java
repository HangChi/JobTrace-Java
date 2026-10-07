package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ImportPreview;
import com.jobtrace.datatransfer.domain.ImportResult;
import com.jobtrace.datatransfer.domain.ImportRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ImportStore {

    void cleanupExpired();

    List<ImportRow> findDuplicates(String ownerId, List<ImportRow> rows);

    ImportPreview savePreview(String ownerId, String filename, String format,
            Map<String, String> mapping, List<ImportRow> rows, UUID replaceBatchId);

    List<ImportResult.Row> confirm(String ownerId, UUID batchId, List<Decision> decisions);

    record Decision(int rowNumber, String action) {}
}
