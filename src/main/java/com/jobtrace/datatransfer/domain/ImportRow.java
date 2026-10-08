package com.jobtrace.datatransfer.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A preview row never contains both normalized data and validation errors. */
public record ImportRow(int rowNumber, Map<String, Object> data, List<ImportError> errors,
        List<UUID> duplicateApplicationIds) {

    public record ImportError(String field, String code, String message) {}

    public ImportRow withDuplicates(List<UUID> ids) {
        return new ImportRow(rowNumber, data, errors, List.copyOf(ids));
    }
}
