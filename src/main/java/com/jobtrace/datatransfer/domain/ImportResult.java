package com.jobtrace.datatransfer.domain;

import java.util.List;
import java.util.UUID;

public record ImportResult(int created, int skipped, int failed, List<Row> rows) {

    public record Row(int rowNumber, String result, UUID applicationId, Error error) {}

    public record Error(String code, String message, String requestId) {}
}
