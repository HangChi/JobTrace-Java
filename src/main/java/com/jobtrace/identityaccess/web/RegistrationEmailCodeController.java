package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.RequestRegistrationEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.domain.RegistrationEmailConflictException;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class RegistrationEmailCodeController {

    public static final String PATH = "/api/auth/email-code";
    private static final String SENT_MESSAGE = "验证码已发送，10 分钟内有效。";

    private final RequestRegistrationEmailCode requestCode;
    private final RegistrationCodeOriginGuard originGuard;
    private final RegistrationEmailCodeMetrics metrics;

    public RegistrationEmailCodeController(RequestRegistrationEmailCode requestCode,
            RegistrationCodeOriginGuard originGuard, RegistrationEmailCodeMetrics metrics) {
        this.requestCode = requestCode;
        this.originGuard = originGuard;
        this.metrics = metrics;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, String>> request(
            @RequestBody Map<String, JsonNode> body, HttpServletRequest request) {
        long started = System.nanoTime();
        try {
            originGuard.assertAllowed(request);
            JsonNode email = body == null ? null : body.get("email");
            if (email == null || !email.isTextual()) {
                throw new AccountEmailCodeValidationException("email");
            }
            requestCode.execute(email.textValue());
            metrics.record("success", elapsed(started));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(Map.of("message", SENT_MESSAGE));
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(RegistrationCodeOriginException.class)
    public ResponseEntity<ProblemDetail> origin(
            RegistrationCodeOriginException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "csrf_rejected", exception.getMessage(), request);
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

    @ExceptionHandler(RegistrationEmailConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(
            RegistrationEmailConflictException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.CONFLICT, "email_conflict",
                exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", "email", "code", "email_conflict",
                "message", exception.getMessage())));
        return response;
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

    @ExceptionHandler(AccountEmailBindingUnavailableException.class)
    public ResponseEntity<ProblemDetail> signingUnavailable(
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
        if (exception instanceof RegistrationCodeOriginException) {
            return "csrf_rejected";
        }
        if (exception instanceof AccountEmailCodeValidationException
                || exception instanceof HttpMessageNotReadableException) {
            return "invalid";
        }
        if (exception instanceof RegistrationEmailConflictException) {
            return "conflict";
        }
        if (exception instanceof AccountEmailCodeRateLimitedException) {
            return "rate_limited";
        }
        if (exception instanceof AccountEmailCodeDeliveryException delivery) {
            return delivery.invalidRecipient() ? "invalid_recipient"
                    : delivery.unavailable() ? "unavailable" : "delivery_failure";
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
