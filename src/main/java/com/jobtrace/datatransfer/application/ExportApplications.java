package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.ExportDownload;
import com.jobtrace.datatransfer.domain.ExportNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Generates an ephemeral, owner-scoped application download. */
@Service
public class ExportApplications {

    private final ExportReadQuery query;
    private final List<ExportFileWriter> writers;
    private final Clock clock;

    public ExportApplications(ExportReadQuery query, List<ExportFileWriter> writers, Clock clock) {
        this.query = query;
        this.writers = List.copyOf(writers);
        this.clock = clock;
    }

    public ExportDownload execute(String ownerId, ApplicationExportSelection selection) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("A verified owner is required");
        }
        var rows = query.applications(ownerId, selection);
        if (rows.isEmpty()) {
            throw new ExportNotFoundException();
        }
        var writer = writers.stream().filter(candidate -> candidate.format() == selection.format())
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing export writer"));
        String extension = selection.format() == ApplicationExportSelection.Format.CSV ? "csv" : "xlsx";
        String mediaType = selection.format() == ApplicationExportSelection.Format.CSV
                ? "text/csv; charset=utf-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String prefix = selection.scope() == ApplicationExportSelection.Scope.SELECTED
                ? "jobtrace-selected-" : "jobtrace-";
        String filename = prefix + LocalDate.now(clock) + "." + extension;
        return new ExportDownload(writer.write(rows), mediaType, filename);
    }
}
