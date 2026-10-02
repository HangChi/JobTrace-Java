package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import com.jobtrace.datatransfer.infrastructure.InterviewMarkdownWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewMarkdownExportContractTest {

    private final InterviewMarkdownWriter writer = new InterviewMarkdownWriter();

    @Test
    void plainMarkdownAndFilenameMatchLegacyShape() {
        var document = new InterviewExportDocument("id", "ACME/科技", "工程师", "interview_1",
                45, List.of(new InterviewExportDocument.Question("# 原文", null, null, null, null)),
                null, null, 0);
        assertThat(writer.content(document)).isEqualTo("# 原文");
        assertThat(writer.filename(document)).isEqualTo("ACME-科技-工程师-一面面经-45分钟.md");
    }

    @Test
    void structuredSectionsPreserveOrderAndOmitEmptyFields() {
        var document = new InterviewExportDocument("id", "C", "P", "assessment", null,
                List.of(new InterviewExportDocument.Question("问题", "回答", null, "改进", 3)),
                "亮点", "不足", 1);
        assertThat(writer.content(document)).isEqualTo("## 面试问题\n\n问题\n\n"
                + "### 当时的回答\n\n回答\n\n### 复盘后的回答\n\n改进\n\n"
                + "## 做得好的地方\n\n亮点\n\n## 可以改进的地方\n\n不足");
        assertThat(writer.filename(document)).isEqualTo("C-P-测评面经-时长未记录.md");
    }

    @Test
    void multipleQuestionsAndEmptyOptionalTextFollowLegacyRules() {
        var document = new InterviewExportDocument("id", "  .. ", "P", "hr_interview", 0,
                List.of(new InterviewExportDocument.Question("一", "", null, null, null),
                        new InterviewExportDocument.Question("二", null, "追问", null, null)),
                "", null, 0);
        assertThat(writer.content(document)).isEqualTo(
                "## 问题 1\n\n一\n\n## 问题 2\n\n二\n\n### 追问或反馈\n\n追问");
        assertThat(writer.filename(document)).endsWith("HR面面经-时长未记录.md");
    }

    @Test
    void stageNameVariantsAreStableAndFilenamePartsAreSafe() {
        for (String stage : List.of("interview_2", "interview_3", "final_interview", "unknown")) {
            var document = new InterviewExportDocument("id", "C", "P", stage, 10,
                    List.of(), null, null, 0);
            assertThat(writer.filename(document)).endsWith("面经-10分钟.md")
                    .doesNotContain("/", "\\");
        }
    }
}
