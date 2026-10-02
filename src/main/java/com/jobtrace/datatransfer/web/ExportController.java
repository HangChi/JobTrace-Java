package com.jobtrace.datatransfer.web;

import com.jobtrace.datatransfer.application.ExportApplications;
import com.jobtrace.datatransfer.application.ExportInterviews;
import com.jobtrace.datatransfer.domain.ApplicationExportSelection;
import com.jobtrace.datatransfer.domain.ExportDownload;
import com.jobtrace.datatransfer.domain.ExportNotFoundException;
import com.jobtrace.datatransfer.domain.InterviewExportSelection;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.shared.web.Problem;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Private read-only export routes. */
@RestController
public class ExportController {

    private final ExportApplications applications;
    private final ExportInterviews interviews;
    private final ExportMetrics metrics;

    public ExportController(ExportApplications applications, ExportInterviews interviews,
            ExportMetrics metrics) {
        this.applications = applications;
        this.interviews = interviews;
        this.metrics = metrics;
    }

    @GetMapping("/api/exports/interviews")
    public ResponseEntity<byte[]> interviews(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        long started = System.nanoTime();
        try {
            var selection = InterviewExportSelection.from(values(parameters, "id"));
            ExportDownload download = interviews.execute(
                    BridgePrincipalOwner.require(principal), selection);
            metrics.record("interviews", "success", elapsed(started));
            return download(download);
        } catch (RuntimeException exception) {
            metrics.record("interviews", outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(ExportNotFoundException.class)
    public ResponseEntity<ProblemDetail> missingExport(
            ExportNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> failedExport(
            RuntimeException exception, HttpServletRequest request) {
        if (exception instanceof Problem known) {
            return problem(known.status(), known.code(), known.getMessage(), request);
        }
        if (exception instanceof IllegalArgumentException) {
            return problem(HttpStatus.BAD_REQUEST, "validation",
                    "The request contains invalid values.", request);
        }
        if (exception instanceof DataAccessException) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable",
                    "The data store is unavailable.", request);
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "The request could not be completed.", request);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String ? requestId : "unavailable");
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }

    @GetMapping("/api/exports/applications")
    public ResponseEntity<byte[]> applications(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        long started = System.nanoTime();
        try {
            var selection = ApplicationExportSelection.from(first(parameters, "scope"),
                    first(parameters, "format"), values(parameters, "id"),
                    first(parameters, "q"), values(parameters, "status"),
                    values(parameters, "type"), values(parameters, "stage"),
                    values(parameters, "city"), first(parameters, "appliedFrom"),
                    first(parameters, "appliedTo"));
            ExportDownload download = applications.execute(
                    BridgePrincipalOwner.require(principal), selection);
            metrics.record("applications", "success", elapsed(started));
            return download(download);
        } catch (RuntimeException exception) {
            metrics.record("applications", outcome(exception), elapsed(started));
            throw exception;
        }
    }

    static ResponseEntity<byte[]> download(ExportDownload download) {
        String ascii = java.text.Normalizer.normalize(download.filename(),
                        java.text.Normalizer.Form.NFKD)
                .replaceAll("[^\\x20-\\x7e]", "_").replaceAll("[\"\\\\]", "_");
        String encoded = java.net.URLEncoder.encode(download.filename(),
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .body(download.content());
    }

    private static String first(MultiValueMap<String, String> parameters, String key) {
        return parameters.getFirst(key);
    }

    private static List<String> values(MultiValueMap<String, String> parameters, String key) {
        return parameters.getOrDefault(key, List.of());
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem problem) {
            return problem.status() == HttpStatus.UNAUTHORIZED ? "denied_identity" : "not_found";
        }
        if (exception instanceof ExportNotFoundException) {
            return "not_found";
        }
        if (exception instanceof IllegalArgumentException) {
            return "invalid";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }
}
