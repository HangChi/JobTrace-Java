package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.RegisterAccount;
import com.jobtrace.identityaccess.application.RegistrationPreflight;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.InvalidRegistrationCodeException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.domain.RegistrationConflictException;
import com.jobtrace.identityaccess.domain.RegistrationValidationException;
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
public class RegistrationController {

    public static final String PATH = "/api/auth/register";

    private final RegistrationCodeOriginGuard originGuard;
    private final RegistrationPreflight preflight;
    private final RegisterAccount registration;
    private final RegistrationMetrics metrics;

    public RegistrationController(RegistrationCodeOriginGuard originGuard,
            RegistrationPreflight preflight, RegisterAccount registration,
            RegistrationMetrics metrics) {
        this.originGuard = originGuard;
        this.preflight = preflight;
        this.registration = registration;
        this.metrics = metrics;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, JsonNode> body,
            HttpServletRequest request) {
        long started = System.nanoTime();
        try {
            originGuard.assertAllowed(request);
            preflight.check(request.getHeader("x-auth-challenge"));
            registration.execute(new RegisterAccount.Input(
                    text(body, "username"), text(body, "email"),
                    text(body, "verificationCode"), text(body, "password"),
                    optionalText(body, "displayName")));
            metrics.record("success", elapsed(started));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(Map.of("message", "账号已创建，请登录。"));
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    private static String text(Map<String, JsonNode> body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || !value.isTextual()) {
            throw new RegistrationValidationException(field, "请求包含无效的注册信息。");
        }
        return value.textValue();
    }

    private static String optionalText(Map<String, JsonNode> body, String field) {
        if (body == null || !body.containsKey(field)) {
            return null;
        }
        return text(body, field);
    }

    @ExceptionHandler(RegistrationCodeOriginException.class)
    public ResponseEntity<ProblemDetail> origin(RegistrationCodeOriginException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "csrf_rejected", exception.getMessage(), request);
    }

    @ExceptionHandler(RegistrationValidationException.class)
    public ResponseEntity<ProblemDetail> validation(RegistrationValidationException exception,
            HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST, "validation", exception.getMessage(),
                request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", exception.field(), "code", "validation",
                "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(InvalidRegistrationCodeException.class)
    public ResponseEntity<ProblemDetail> invalidCode(InvalidRegistrationCodeException exception,
            HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST, "invalid_email_code",
                exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", "verificationCode", "code", "invalid_email_code",
                "message", "请检查验证码，或重新获取。")));
        return response;
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(RegistrationConflictException exception,
            HttpServletRequest request) {
        var response = problem(HttpStatus.CONFLICT, "registration_conflict",
                exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of(
                "field", exception.field(), "code", "registration_conflict",
                "message", exception.fieldMessage())));
        return response;
    }

    @ExceptionHandler(AccountEmailCodeRateLimitedException.class)
    public ResponseEntity<ProblemDetail> rateLimited(
            AccountEmailCodeRateLimitedException exception, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", exception.getMessage(),
                request);
    }

    @ExceptionHandler(RegistrationChallengeException.class)
    public ResponseEntity<ProblemDetail> challenge(RegistrationChallengeException exception,
            HttpServletRequest request) {
        return switch (exception.reason()) {
            case REQUIRED -> problem(HttpStatus.BAD_REQUEST, "challenge_required",
                    exception.getMessage(), request);
            case FAILED -> problem(HttpStatus.FORBIDDEN, "challenge_failed",
                    exception.getMessage(), request);
            case UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "challenge_unavailable",
                    exception.getMessage(), request);
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "请求包含无效的注册信息。", request);
    }

    @ExceptionHandler(AccountEmailBindingUnavailableException.class)
    public ResponseEntity<ProblemDetail> signingUnavailable(
            AccountEmailBindingUnavailableException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "email_verification_unavailable",
                exception.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> databaseUnavailable(DataAccessException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "registration_unavailable",
                "暂时无法完成注册，请稍后重试。", request);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof RegistrationCodeOriginException) {
            return "csrf_rejected";
        }
        if (exception instanceof RegistrationValidationException) {
            return "invalid";
        }
        if (exception instanceof InvalidRegistrationCodeException) {
            return "invalid_code";
        }
        if (exception instanceof RegistrationConflictException) {
            return "conflict";
        }
        if (exception instanceof AccountEmailCodeRateLimitedException) {
            return "rate_limited";
        }
        if (exception instanceof RegistrationChallengeException challenge) {
            return switch (challenge.reason()) {
                case REQUIRED -> "challenge_required";
                case FAILED -> "challenge_failed";
                case UNAVAILABLE -> "unavailable";
            };
        }
        if (exception instanceof AccountEmailBindingUnavailableException
                || exception instanceof DataAccessException) {
            return "unavailable";
        }
        return "failure";
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code,
            String detail, HttpServletRequest request) {
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
