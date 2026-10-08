package com.jobtrace.datatransfer.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ImportPreview(UUID id, Instant expiresAt, int totalRows, int validRows,
        int invalidRows, int duplicateRows, Map<String, String> columns, List<ImportRow> rows) {}
