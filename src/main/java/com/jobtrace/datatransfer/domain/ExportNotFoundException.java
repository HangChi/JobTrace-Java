package com.jobtrace.datatransfer.domain;

/** No owned records matched the requested private download. */
public final class ExportNotFoundException extends RuntimeException {
    public ExportNotFoundException() {
        super("No matching export records were found.");
    }
}
