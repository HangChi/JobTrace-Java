package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobtrace.datatransfer.application.ExportApplications;
import com.jobtrace.datatransfer.application.ExportFileWriter;
import com.jobtrace.datatransfer.application.ExportInterviews;
import com.jobtrace.datatransfer.application.ExportReadQuery;
import com.jobtrace.datatransfer.domain.ApplicationExportRow;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.ExportNotFoundException;
import com.jobtrace.datatransfer.web.ExportController;
import com.jobtrace.datatransfer.web.ExportMetrics;
import com.jobtrace.shared.web.RequestIdFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataAccessResourceFailureException;

class ExportFailureAndMetricsTest {

    @Test
    void writerFailureCannotBecomeAPartialDownload() {
        var selection = ApplicationExportSelection.from("all", "csv", List.of(), null,
                List.of(), List.of(), List.of(), List.of(), null, null);
        var query = mock(ExportReadQuery.class);
        when(query.applications("owner-a", selection)).thenReturn(List.of(new ApplicationExportRow(
                "id", "company", "role", null, null, "2026-10-01", "春招", "已投递",
                "2026-10-01", "", null, "2026-10-01T00:00:00.000Z",
                "2026-10-01T00:00:00.000Z")));
        ExportFileWriter broken = new ExportFileWriter() {
            @Override public ApplicationExportSelection.Format format() {
                return ApplicationExportSelection.Format.CSV;
            }
            @Override public byte[] write(List<ApplicationExportRow> rows) {
                throw new IllegalStateException("private writer detail");
            }
        };
        var useCase = new ExportApplications(query, List.of(broken), Clock.systemUTC());
        assertThatThrownBy(() -> useCase.execute("owner-a", selection))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void safeNotFoundProblemContainsOnlyRequestId() {
        var controller = new ExportController(mock(ExportApplications.class),
                mock(ExportInterviews.class), new ExportMetrics(new SimpleMeterRegistry()));
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "safe-request-id");
        var response = controller.missingExport(new ExportNotFoundException(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getProperties()).containsEntry("requestId", "safe-request-id")
                .containsEntry("code", "not_found");
        assertThat(response.getBody().getDetail()).doesNotContain("owner", "writer");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        var writerFailure = controller.failedExport(
                new IllegalStateException("private writer detail owner-a"), request);
        assertThat(writerFailure.getStatusCode().value()).isEqualTo(500);
        assertThat(writerFailure.getBody().getDetail()).doesNotContain("owner-a", "writer");
        assertThat(controller.failedExport(new DataAccessResourceFailureException("secret"),
                request).getStatusCode().value()).isEqualTo(503);
        assertThat(controller.failedExport(new IllegalArgumentException("secret"),
                request).getStatusCode().value()).isEqualTo(400);
    }
}
