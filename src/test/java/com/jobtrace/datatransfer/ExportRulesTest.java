package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import com.jobtrace.datatransfer.infrastructure.CsvExportWriter;
import com.jobtrace.datatransfer.infrastructure.InterviewMarkdownWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportRulesTest {

    private static final String ID = "00000000-0000-0000-0000-000000000101";

    @Test
    void applicationDefaultsAndSelectedBoundsMatchCurrentRoute() {
        var fallback = ApplicationExportSelection.from(null, "unknown", List.of(),
                null, List.of(), List.of(), List.of(), List.of(), null, null);
        assertThat(fallback.scope()).isEqualTo(ApplicationExportSelection.Scope.FILTERED);
        assertThat(fallback.format()).isEqualTo(ApplicationExportSelection.Format.XLSX);
        assertThat(fallback.ids()).isEmpty();
        var selected = ApplicationExportSelection.from("selected", "csv", List.of(ID),
                null, List.of(), List.of(), List.of(), List.of(), null, null);
        assertThat(selected.ids()).hasSize(1);
        assertThat(selected.format()).isEqualTo(ApplicationExportSelection.Format.CSV);
        assertThatThrownBy(() -> ApplicationExportSelection.from("selected", "csv", List.of(),
                null, List.of(), List.of(), List.of(), List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationExportSelection.from("selected", "csv",
                java.util.Collections.nCopies(101, ID), null, List.of(), List.of(),
                List.of(), List.of(), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationExportSelection.from("selected", "csv",
                List.of("not-a-uuid"), null, List.of(), List.of(), List.of(), List.of(),
                null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interviewSelectionDeduplicatesWithoutChangingOrder() {
        String second = "00000000-0000-0000-0000-000000000102";
        var selected = InterviewExportSelection.from(List.of(ID, second, ID));
        assertThat(selected.ids()).extracting(Object::toString).containsExactly(ID, second);
        assertThatThrownBy(() -> InterviewExportSelection.from(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterviewExportSelection.from(
                java.util.Collections.nCopies(101, ID)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterviewExportSelection.from(List.of("bad")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selected.ids().add(java.util.UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void csvTextAndFilenameRulesAreSafe() {
        for (String prefix : List.of("=", "+", "-", "@", "\t", "\r")) {
            assertThat(CsvExportWriter.safeFormula(prefix + "2+3"))
                    .isEqualTo("'" + prefix + "2+3");
        }
        assertThat(CsvExportWriter.csvCell("a,\"b\"\nc"))
                .isEqualTo("\"a,\"\"b\"\"\nc\"");
        assertThat(InterviewMarkdownWriter.safeFilenamePart("  ../a:b\\c?  "))
                .doesNotContain("/", "\\", ":", "?")
                .isNotBlank();
        assertThat(InterviewMarkdownWriter.safeFilenamePart(" ")).isEqualTo("未命名");
        assertThat(CsvExportWriter.safeFormula(null)).isEmpty();
        assertThat(CsvExportWriter.safeFormula("")).isEmpty();
        assertThat(CsvExportWriter.safeFormula("ordinary")).isEqualTo("ordinary");
        assertThat(CsvExportWriter.csvCell(null)).isEmpty();
        assertThat(CsvExportWriter.csvCell("ordinary")).isEqualTo("ordinary");
    }
}
