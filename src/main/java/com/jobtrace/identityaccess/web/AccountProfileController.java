package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.UpdateAccountProfile;
import com.jobtrace.identityaccess.domain.AccountProfileNotFoundException;
import com.jobtrace.identityaccess.domain.AccountProfileResult;
import com.jobtrace.shared.web.Problem;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class AccountProfileController {

    public static final String PATH = "/api/profile";

    private final UpdateAccountProfile updateProfile;
    private final AccountProfileMetrics metrics;

    public AccountProfileController(
            UpdateAccountProfile updateProfile,
            AccountProfileMetrics metrics) {
        this.updateProfile = updateProfile;
        this.metrics = metrics;
    }

    @PatchMapping(PATH)
    public ResponseEntity<AccountProfileResult> update(
            @RequestBody Map<String, JsonNode> body,
            Principal principal) {
        long started = System.nanoTime();
        try {
            AccountProfileUpdateRequest request = AccountProfileUpdateRequest.from(body);
            AccountProfileResult result = updateProfile.execute(
                    BridgePrincipalOwner.require(principal),
                    request.displayName(), request.image());
            metrics.record("success", elapsed(started));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(result);
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(AccountProfileNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(
            AccountProfileNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation",
                "The request contains invalid values.", request);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem) {
            return "denied_identity";
        }
        if (exception instanceof IllegalArgumentException
                || exception instanceof HttpMessageNotReadableException) {
            return "invalid";
        }
        if (exception instanceof AccountProfileNotFoundException) {
            return "not_found";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }
}
