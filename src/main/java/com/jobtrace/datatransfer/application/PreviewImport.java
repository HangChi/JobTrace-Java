package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ImportPreview;
import com.jobtrace.datatransfer.domain.ImportRowNormalizer;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PreviewImport {

    private final ImportFileReader reader;
    private final ImportStore store;

    public PreviewImport(ImportFileReader reader, ImportStore store) {
        this.reader = reader;
        this.store = store;
    }

    public ImportPreview execute(String ownerId, String filename, byte[] contents,
            Map<String, String> requestedMapping, UUID replaceBatchId) {
        store.cleanupExpired();
        var spreadsheet = reader.read(contents, filename);
        var mapping = ImportRowNormalizer.validateMapping(spreadsheet.columns(), requestedMapping);
        var rows = new ArrayList<com.jobtrace.datatransfer.domain.ImportRow>();
        for (int index = 0; index < spreadsheet.rows().size(); index++) {
            rows.add(ImportRowNormalizer.normalize(index + 2, spreadsheet.rows().get(index), mapping));
        }
        String safeName = filename == null ? "import" : filename.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N}._-]", "_");
        safeName = safeName.isBlank() ? "import" : safeName.substring(0, Math.min(255, safeName.length()));
        String format = filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx") ? "xlsx" : "csv";
        return store.savePreview(ownerId, safeName, format, mapping,
                store.findDuplicates(ownerId, rows), replaceBatchId);
    }
}
