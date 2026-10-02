package com.jobtrace.interviews.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.interviews.application.GetPrivateInterview;
import com.jobtrace.interviews.application.ListPrivateInterviews;
import com.jobtrace.interviews.domain.InterviewDetail;
import com.jobtrace.interviews.domain.InterviewNotFoundException;
import com.jobtrace.interviews.domain.InterviewPage;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
public class InterviewReadController {

    private final ListPrivateInterviews listInterviews;
    private final GetPrivateInterview getInterview;
    private final InterviewReadMetrics metrics;

    public InterviewReadController(ListPrivateInterviews listInterviews,
            GetPrivateInterview getInterview, InterviewReadMetrics metrics) {
        this.listInterviews = listInterviews;
        this.getInterview = getInterview;
        this.metrics = metrics;
    }

    @GetMapping
    public ResponseEntity<InterviewPage> list(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        long started = System.nanoTime();
        try {
            InterviewPage page = listInterviews.execute(
                    BridgePrincipalOwner.require(principal), InterviewListParameters.from(parameters));
            record("list", "success", started);
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(page);
        } catch (RuntimeException exception) {
            record("list", outcome(exception), started);
            throw exception;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewDetail> detail(@PathVariable UUID id, Principal principal) {
        long started = System.nanoTime();
        try {
            InterviewDetail detail = getInterview.execute(BridgePrincipalOwner.require(principal), id);
            record("detail", "success", started);
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
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
        if (exception instanceof IllegalArgumentException) {
            return "invalid";
        }
        if (exception instanceof InterviewNotFoundException) {
            return "not_found";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }
}
