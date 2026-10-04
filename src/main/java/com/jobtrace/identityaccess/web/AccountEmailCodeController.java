package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.RequestAccountEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
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
public class AccountEmailCodeController {

    public static final String PATH = "/api/profile/email/code";
    private static final String SENT_MESSAGE = "验证码已发送，10 分钟内有效。";

    private final RequestAccountEmailCode requestCode;
    private final AccountEmailCodeMetrics metrics;

    public AccountEmailCodeController(RequestAccountEmailCode requestCode,
            AccountEmailCodeMetrics metrics) {
        this.requestCode = requestCode;
        this.metrics = metrics;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, String>> request(
            @RequestHeader(value = AccountSessionReadController.CURRENT_SESSION_HEADER,
                    required = false) String currentSessionId,
            @RequestBody Map<String, JsonNode> body,
            Principal principal) {
        long started = System.nanoTime();
        try {
            JsonNode email = body == null ? null : body.get("email");
            if (email == null || !email.isTextual()) {
                throw new AccountEmailCodeValidationException("email");
            }
            requestCode.execute(BridgePrincipalOwner.require(principal),
                    BridgePrincipalOwner.currentSessionId(principal, currentSessionId),
                    email.textValue());
            metrics.record("success", elapsed(started));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(Map.of("message", SENT_MESSAGE));
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(AccountEmailCodeValidationException.class)
    public ResponseEntity<ProblemDetail> validation(
            AccountEmailCodeValidationException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST, "validation",
                exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", exception.field(), "code", "validation",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(AccountEmailConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(
            AccountEmailConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "email_conflict", exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailCodeRateLimitedException.class)
    public ResponseEntity<ProblemDetail> rateLimited(
            AccountEmailCodeRateLimitedException exception, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailCodeDeliveryException.class)
    public ResponseEntity<ProblemDetail> deliveryFailure(
            AccountEmailCodeDeliveryException exception, HttpServletRequest request) {
        if (exception.invalidRecipient()) {
            var response = problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_recipient",
                    exception.getMessage(), request);
            response.getBody().setProperty("fieldErrors", List.of(Map.of(
                    "field", "email", "code", "invalid_recipient",
                    "message", exception.getMessage())));
            return response;
        }
        return problem(exception.unavailable() ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.BAD_GATEWAY,
                exception.unavailable() ? "email_delivery_unavailable" : "email_delivery_failed",
                exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailCodeStateException.class)
    public ResponseEntity<ProblemDetail> invalidState(
            AccountEmailCodeStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "email_code_failed",
                exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailBindingUnavailableException.class)
    public ResponseEntity<ProblemDetail> missingSigningSecret(
            AccountEmailBindingUnavailableException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_unavailable",
                exception.getMessage(), request);
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
        if (exception instanceof AccountEmailCodeValidationException
                || exception instanceof HttpMessageNotReadableException) {
            return "invalid";
        }
        if (exception instanceof AccountEmailConflictException) {
            return "conflict";
        }
        if (exception instanceof AccountEmailCodeRateLimitedException) {
            return "rate_limited";
        }
        if (exception instanceof AccountEmailCodeDeliveryException delivery) {
            return delivery.invalidRecipient() ? "invalid_recipient"
                    : delivery.unavailable() ? "unavailable" : "delivery_failure";
        }
        if (exception instanceof AccountEmailCodeStateException) {
            return "invalid_state";
        }
        if (exception instanceof AccountEmailBindingUnavailableException) {
            return "unavailable";
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
