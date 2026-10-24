package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ImportResult;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConfirmImport {

    private final ImportStore store;

    public ConfirmImport(ImportStore store) {
        this.store = store;
    }

    public ImportResult execute(String ownerId, UUID batchId,
            List<ImportStore.Decision> decisions) {
        store.cleanupExpired();
        List<ImportResult.Row> rows = store.confirm(ownerId, batchId, decisions);
        int created = (int) rows.stream().filter(row -> "created".equals(row.result())).count();
        int skipped = (int) rows.stream().filter(row -> "skipped".equals(row.result())).count();
        int failed = (int) rows.stream().filter(row -> "failed".equals(row.result())).count();
        return new ImportResult(created, skipped, failed, rows);
    }
}
