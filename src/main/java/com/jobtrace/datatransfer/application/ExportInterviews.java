package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ExportDownload;
import com.jobtrace.datatransfer.domain.ExportNotFoundException;
import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Generates a single private Markdown file or an ordered ZIP archive. */
@Service
public class ExportInterviews {

    private final ExportReadQuery query;
    private final InterviewFileRenderer renderer;
    private final Clock clock;

    public ExportInterviews(ExportReadQuery query, InterviewFileRenderer renderer, Clock clock) {
        this.query = query;
        this.renderer = renderer;
        this.clock = clock;
    }

    public ExportDownload execute(String ownerId, InterviewExportSelection selection) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("A verified owner is required");
        }
        var documents = query.interviews(ownerId, selection);
        if (documents.isEmpty()) {
            throw new ExportNotFoundException();
        }
        if (documents.size() == 1) {
            var document = documents.getFirst();
            return new ExportDownload(renderer.markdown(document),
                    "text/markdown; charset=utf-8", renderer.filename(document));
        }
        return new ExportDownload(renderer.zip(documents), "application/zip",
                "JobTrace-面经-" + LocalDate.now(clock) + ".zip");
    }
}
