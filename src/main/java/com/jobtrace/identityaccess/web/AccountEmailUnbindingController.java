package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.UnbindAccountEmail;
import com.jobtrace.identityaccess.domain.AccountEmailRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailUnbindingResult;
import com.jobtrace.identityaccess.domain.AccountEmailUnbindingStateException;
import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import com.jobtrace.identityaccess.domain.InvalidCurrentPasswordException;
import com.jobtrace.shared.web.Problem;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class AccountEmailUnbindingController {

    public static final String PATH = "/api/profile/email";

    private final UnbindAccountEmail unbindEmail;
    private final AccountEmailUnbindingMetrics metrics;

    public AccountEmailUnbindingController(
            UnbindAccountEmail unbindEmail, AccountEmailUnbindingMetrics metrics) {
        this.unbindEmail = unbindEmail;
        this.metrics = metrics;
    }

    @DeleteMapping(PATH)
    public ResponseEntity<AccountEmailUnbindingResult> unbind(
            @RequestHeader(
                    value = AccountSessionReadController.CURRENT_SESSION_HEADER,
                    required = false) String currentSessionId,
            @RequestBody Map<String, JsonNode> body,
            Principal principal) {
        long started = System.nanoTime();
        try {
            String password = AccountEmailUnbindingRequest.from(body).currentPasswordValue();
            var result = unbindEmail.execute(
                    BridgePrincipalOwner.require(principal),
                    BridgePrincipalOwner.currentSessionId(principal, currentSessionId), password);
            metrics.record("success", elapsed(started));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(result);
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(AccountPasswordValidationException.class)
    public ResponseEntity<ProblemDetail> validation(
            AccountPasswordValidationException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST, "validation",
                exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", exception.field(), "code", "validation",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ProblemDetail> invalidPassword(
            InvalidCurrentPasswordException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST, "invalid_password",
                exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", "currentPassword", "code", "invalid_password",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(AccountEmailRateLimitedException.class)
    public ResponseEntity<ProblemDetail> rateLimited(
            AccountEmailRateLimitedException exception, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS,
                "rate_limited", exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailUnbindingStateException.class)
    public ResponseEntity<ProblemDetail> invalidState(
            AccountEmailUnbindingStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "email_unbind_failed", exception.getMessage(), request);
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
        if (exception instanceof AccountPasswordValidationException
                || exception instanceof HttpMessageNotReadableException) {
            return "invalid";
        }
        if (exception instanceof InvalidCurrentPasswordException) {
            return "invalid_password";
        }
        if (exception instanceof AccountEmailRateLimitedException) {
            return "rate_limited";
        }
        if (exception instanceof AccountEmailUnbindingStateException) {
            return "invalid_state";
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
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}
