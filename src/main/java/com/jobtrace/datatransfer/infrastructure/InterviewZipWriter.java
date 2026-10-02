package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/** Ordered, collision-safe ZIP packaging of private review Markdown. */
@Component
public class InterviewZipWriter {

    private final InterviewMarkdownWriter markdown = new InterviewMarkdownWriter();

    public byte[] write(List<InterviewExportDocument> documents) {
        var names = new HashMap<String, Integer>();
        try (var output = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var document : documents) {
                String preferred = markdown.filename(document);
                int count = names.merge(preferred, 1, Integer::sum);
                String name = count == 1 ? preferred
                        : preferred.substring(0, preferred.length() - 3) + "-" + count + ".md";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(markdown.content(document).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to create interview export", exception);
        }
    }
}
