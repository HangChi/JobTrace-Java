package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.application.InterviewFileRenderer;
import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InterviewFileRendererAdapter implements InterviewFileRenderer {

    private final InterviewMarkdownWriter markdown = new InterviewMarkdownWriter();
    private final InterviewZipWriter zip;

    public InterviewFileRendererAdapter(InterviewZipWriter zip) {
        this.zip = zip;
    }

    @Override
    public String filename(InterviewExportDocument document) {
        return markdown.filename(document);
    }

    @Override
    public byte[] markdown(InterviewExportDocument document) {
        return markdown.content(document).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] zip(List<InterviewExportDocument> documents) {
        return zip.write(documents);
    }
}
