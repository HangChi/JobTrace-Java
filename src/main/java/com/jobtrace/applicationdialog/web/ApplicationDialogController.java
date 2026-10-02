package com.jobtrace.applicationdialog.web;

import com.jobtrace.applicationdialog.application.ApplicationDialogData;
import com.jobtrace.applicationdialog.application.GetApplicationDialogData;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.interviews.web.InterviewReadMetrics;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationDialogController {

    private final GetApplicationDialogData getDialog;
    private final InterviewReadMetrics metrics;

    public ApplicationDialogController(GetApplicationDialogData getDialog, InterviewReadMetrics metrics) {
        this.getDialog = getDialog;
        this.metrics = metrics;
    }

    @GetMapping("/api/applications/{id}/detail")
    public ResponseEntity<ApplicationDialogData> detail(
            @PathVariable UUID id, Principal principal) {
        long started = System.nanoTime();
        try {
            ApplicationDialogData data = getDialog.execute(
                    BridgePrincipalOwner.require(principal), id);
            record("success", started);
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(data);
        } catch (RuntimeException exception) {
            record(outcome(exception), started);
            throw exception;
        }
    }

    private void record(String outcome, long started) {
        metrics.record("application_dialog", outcome,
                Duration.ofNanos(System.nanoTime() - started));
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem problem && problem.status() == HttpStatus.UNAUTHORIZED) {
            return "denied_identity";
        }
        if (exception instanceof ApplicationNotFoundException) {
            return "not_found";
        }
        if (exception instanceof IllegalArgumentException) {
            return "invalid";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }
}
