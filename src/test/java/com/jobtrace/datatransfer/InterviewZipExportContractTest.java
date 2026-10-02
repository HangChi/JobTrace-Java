package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import com.jobtrace.datatransfer.infrastructure.InterviewZipWriter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class InterviewZipExportContractTest {

    @Test
    void duplicateDescriptiveNamesAreSuffixedInSelectedOrder() throws Exception {
        var first = new InterviewExportDocument("1", "A/公司", "岗位", "interview_1", 45,
                List.of(new InterviewExportDocument.Question("第一篇", null, null, null, null)),
                null, null, 0);
        var second = new InterviewExportDocument("2", "A/公司", "岗位", "interview_1", 45,
                List.of(new InterviewExportDocument.Question("第二篇", null, null, null, null)),
                null, null, 0);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(
                new InterviewZipWriter().write(List.of(first, second))), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("A-公司-岗位-一面面经-45分钟.md");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("第一篇");
            assertThat(zip.getNextEntry().getName()).isEqualTo("A-公司-岗位-一面面经-45分钟-2.md");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("第二篇");
            assertThat(zip.getNextEntry()).isNull();
        }
    }
}
