package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.jobtrace.datatransfer.application.ExportApplications;
import com.jobtrace.datatransfer.application.ExportReadQuery;
import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.infrastructure.CsvExportWriter;
import com.jobtrace.datatransfer.domain.ExportNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExportApplicationsTest {

    private static final ApplicationExportSelection SELECTION = ApplicationExportSelection.from(
            "all", "csv", List.of(), null, List.of(), List.of(), List.of(), List.of(), null, null);

    @Test
    void requiresOwnerAndReturnsDatedPrivateDownload() {
        var query = Mockito.mock(ExportReadQuery.class);
        var useCase = new ExportApplications(query, List.of(new CsvExportWriter()),
                Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> useCase.execute(" ", SELECTION))
                .isInstanceOf(IllegalArgumentException.class);
        when(query.applications("owner-a", SELECTION)).thenReturn(List.of(new ApplicationExportRow(
                "id", "Company", "Engineer", null, null, "2026-09-01", "春招", "已投递",
                "2026-09-01", "", null, "2026-09-01T00:00:00.000Z",
                "2026-09-01T00:00:00.000Z")));
        var download = useCase.execute("owner-a", SELECTION);
        assertThat(download.filename()).isEqualTo("jobtrace-2026-10-02.csv");
        assertThat(download.mediaType()).isEqualTo("text/csv; charset=utf-8");
        assertThat(download.content()).isNotEmpty();
    }

    @Test
    void emptyAndFailedQueriesDoNotProduceAFile() {
        var query = Mockito.mock(ExportReadQuery.class);
        var useCase = new ExportApplications(query, List.of(new CsvExportWriter()), Clock.systemUTC());
        when(query.applications("owner-a", SELECTION)).thenReturn(List.of());
        assertThatThrownBy(() -> useCase.execute("owner-a", SELECTION))
                .isInstanceOf(ExportNotFoundException.class);
        when(query.applications(eq("owner-b"), any())).thenThrow(
                new IllegalStateException("storage failed"));
        assertThatThrownBy(() -> useCase.execute("owner-b", SELECTION))
                .isInstanceOf(IllegalStateException.class);
    }
}
