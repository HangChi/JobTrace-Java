package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import java.util.List;

/** Private review file encoding port. */
public interface InterviewFileRenderer {
    String filename(InterviewExportDocument document);
    byte[] markdown(InterviewExportDocument document);
    byte[] zip(List<InterviewExportDocument> documents);
}
