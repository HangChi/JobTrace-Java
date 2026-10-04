package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.ChangeAccountPassword;
import com.jobtrace.identityaccess.domain.AccountPasswordChangeResult;
import com.jobtrace.identityaccess.domain.AccountPasswordStateException;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class AccountPasswordController {

    public static final String PATH = "/api/profile/password";

    private final ChangeAccountPassword changePassword;
    private final AccountPasswordMetrics metrics;

    public AccountPasswordController(
            ChangeAccountPassword changePassword,
            AccountPasswordMetrics metrics) {
        this.changePassword = changePassword;
        this.metrics = metrics;
    }

    @PostMapping(PATH)
    public ResponseEntity<AccountPasswordChangeResult> change(
            @RequestHeader(
                    value = AccountSessionReadController.CURRENT_SESSION_HEADER,
                    required = false) String currentSessionId,
            @RequestBody Map<String, JsonNode> body,
            Principal principal) {
        long started = System.nanoTime();
        try {
            AccountPasswordChangeRequest request = AccountPasswordChangeRequest.from(body);
            AccountPasswordChangeResult result = changePassword.execute(
                    BridgePrincipalOwner.require(principal),
                    BridgePrincipalOwner.currentSessionId(principal, currentSessionId),
                    request.currentPasswordValue(),
                    request.newPasswordValue());
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
            AccountPasswordValidationException exception,
            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.BAD_REQUEST, "validation", exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", exception.field(),
                "code", "validation",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ProblemDetail> invalidPassword(
            InvalidCurrentPasswordException exception,
            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.BAD_REQUEST, "invalid_password", exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", "currentPassword",
                "code", "invalid_password",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(AccountPasswordStateException.class)
    public ResponseEntity<ProblemDetail> invalidState(
            AccountPasswordStateException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "password_change_failed", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
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
        if (exception instanceof AccountPasswordStateException) {
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
