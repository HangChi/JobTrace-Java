package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.BindAccountEmail;
import com.jobtrace.identityaccess.domain.AccountEmailBindingInvalidPasswordException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingResult;
import com.jobtrace.identityaccess.domain.AccountEmailBindingStateException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.AccountEmailRateLimitedException;
import com.jobtrace.identityaccess.domain.InvalidEmailBindingCodeException;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class AccountEmailBindingController {

    public static final String PATH = "/api/profile/email";

    private final BindAccountEmail bindEmail;
    private final AccountEmailBindingMetrics metrics;

    public AccountEmailBindingController(
            BindAccountEmail bindEmail, AccountEmailBindingMetrics metrics) {
        this.bindEmail = bindEmail;
        this.metrics = metrics;
    }

    @PatchMapping(PATH)
    public ResponseEntity<AccountEmailBindingResult> bind(
            @RequestHeader(
                    value = AccountSessionReadController.CURRENT_SESSION_HEADER,
                    required = false) String currentSessionId,
            @RequestBody Map<String, JsonNode> body,
            Principal principal) {
        long started = System.nanoTime();
        try {
            var request = AccountEmailBindingRequest.from(body);
            var result = bindEmail.execute(BridgePrincipalOwner.require(principal),
                    currentSessionId, request.passwordValue(), request.emailValue(),
                    request.codeValue());
            metrics.record("success", elapsed(started));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(result);
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(AccountEmailBindingValidationException.class)
    public ResponseEntity<ProblemDetail> validation(
            AccountEmailBindingValidationException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST,
                "validation", exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", exception.field(), "code", "validation",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(AccountEmailBindingInvalidPasswordException.class)
    public ResponseEntity<ProblemDetail> invalidPassword(
            AccountEmailBindingInvalidPasswordException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST,
                "invalid_password", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidEmailBindingCodeException.class)
    public ResponseEntity<ProblemDetail> invalidCode(
            InvalidEmailBindingCodeException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST,
                "invalid_email_code", exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", "verificationCode", "code", "invalid_email_code",
                "message", "请检查验证码，或重新获取。")));
        return response;
    }

    @ExceptionHandler(AccountEmailConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(
            AccountEmailConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT,
                "email_conflict", exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailRateLimitedException.class)
    public ResponseEntity<ProblemDetail> rateLimited(
            AccountEmailRateLimitedException exception, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS,
                "rate_limited", exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailBindingUnavailableException.class)
    public ResponseEntity<ProblemDetail> unavailable(
            AccountEmailBindingUnavailableException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
                "email_verification_unavailable", exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailBindingStateException.class)
    public ResponseEntity<ProblemDetail> invalidState(
            AccountEmailBindingStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "email_bind_failed", exception.getMessage(), request);
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
        if (exception instanceof AccountEmailBindingValidationException
                || exception instanceof HttpMessageNotReadableException) {
            return "invalid";
        }
        if (exception instanceof AccountEmailBindingInvalidPasswordException) {
            return "invalid_password";
        }
        if (exception instanceof InvalidEmailBindingCodeException) {
            return "invalid_code";
        }
        if (exception instanceof AccountEmailConflictException) {
            return "conflict";
        }
        if (exception instanceof AccountEmailRateLimitedException) {
            return "rate_limited";
        }
        if (exception instanceof AccountEmailBindingUnavailableException) {
            return "unavailable";
        }
        if (exception instanceof AccountEmailBindingStateException) {
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
