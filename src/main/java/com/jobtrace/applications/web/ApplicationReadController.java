package com.jobtrace.applications.web;

import com.jobtrace.applications.application.GetApplicationDetail;
import com.jobtrace.applications.application.ListApplications;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.applications.domain.ApplicationPage;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationReadController {

    private final ListApplications listApplications;
    private final GetApplicationDetail getApplicationDetail;
    private final ApplicationReadMetrics metrics;

    public ApplicationReadController(
            ListApplications listApplications,
            GetApplicationDetail getApplicationDetail,
            ApplicationReadMetrics metrics) {
        this.listApplications = listApplications;
        this.getApplicationDetail = getApplicationDetail;
        this.metrics = metrics;
    }

    @GetMapping
    public ResponseEntity<ApplicationPage> list(
            @RequestParam MultiValueMap<String, String> parameters,
            Principal principal) {
        long started = System.nanoTime();
        try {
            ApplicationPage page = listApplications.execute(
                    owner(principal), ApplicationListParameters.from(parameters));
            record("list", "success", started);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(page);
        } catch (RuntimeException exception) {
            record("list", outcome(exception), started);
            throw exception;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDetail> detail(@PathVariable UUID id, Principal principal) {
        long started = System.nanoTime();
        try {
            ApplicationDetail detail = getApplicationDetail.execute(owner(principal), id);
            record("detail", "success", started);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(detail);
        } catch (RuntimeException exception) {
            record("detail", outcome(exception), started);
            throw exception;
        }
    }

    private void record(String operation, String outcome, long started) {
        metrics.record(operation, outcome, Duration.ofNanos(System.nanoTime() - started));
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

    private static String owner(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getDetails() instanceof BridgeIdentity identity
                && identity.subject().equals(authentication.getName())) {
            return identity.subject();
        }
        throw new Problem("unauthorized", "Authentication is required.", HttpStatus.UNAUTHORIZED);
    }
}
