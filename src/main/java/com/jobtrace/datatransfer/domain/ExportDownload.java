package com.jobtrace.datatransfer.domain;

import java.util.Objects;

/** Private ephemeral download payload. */
public record ExportDownload(byte[] content, String mediaType, String filename) {

    public ExportDownload {
        content = content.clone();
        Objects.requireNonNull(mediaType);
        Objects.requireNonNull(filename);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
