package com.jobtrace.datatransfer.application;

import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection.Format;
import java.util.List;

/** Application spreadsheet encoding port. */
public interface ExportFileWriter {

    Format format();

    byte[] write(List<ApplicationExportRow> rows);
}
