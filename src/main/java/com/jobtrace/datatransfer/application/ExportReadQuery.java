package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import java.util.List;

/** Owner-bound read port; no application or review mutation operation exists here. */
public interface ExportReadQuery {

    List<ApplicationExportRow> applications(String ownerId, ApplicationExportSelection selection);

    List<InterviewExportDocument> interviews(String ownerId, InterviewExportSelection selection);
}
