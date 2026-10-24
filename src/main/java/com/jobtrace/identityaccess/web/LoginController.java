package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.LoginAccount;
import com.jobtrace.identityaccess.application.LoginPreflight;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.InvalidCredentialsException;
import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.domain.LoginValidationException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
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
import tools.jackson.databind.ObjectMapper;

@RestController
public class LoginController {
    public static final String PATH = "/api/auth/login";
    private final RegistrationCodeOriginGuard origin;
    private final LoginPreflight preflight;
    private final LoginAccount login;
    private final BetterAuthSessionCookie cookie;
    private final LoginMetrics metrics;
    private final ObjectMapper mapper;

    public LoginController(RegistrationCodeOriginGuard origin, LoginPreflight preflight,
            LoginAccount login, BetterAuthSessionCookie cookie, LoginMetrics metrics,
            ObjectMapper mapper) {
        this.origin = origin;
        this.preflight = preflight;
        this.login = login;
        this.cookie = cookie;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, String>> login(@RequestBody String rawBody,
            HttpServletRequest request) {
        long started = System.nanoTime();
        try {
            origin.assertAllowed(request);
            preflight.check(request.getHeader("x-auth-challenge"));
            cookie.ensureAvailable();
            JsonNode body = parse(rawBody);
            String identifier = optionalText(body, "identifier");
            if (identifier == null) {
                identifier = text(body, "username");
            }
            var result = login.execute(new LoginAccount.Input(identifier, text(body, "password"),
                    optionalText(body, "returnTo"), request.getHeader("user-agent")));
            metrics.record("success", elapsed(started));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header(HttpHeaders.SET_COOKIE, cookie.header(result.sessionToken()))
                    .body(Map.of("redirectTarget", result.redirectTarget()));
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    private JsonNode parse(String rawBody) {
        try {
            JsonNode body = mapper.readTree(rawBody);
            if (body == null || !body.isObject()) {
                throw new LoginValidationException("identifier");
            }
            return body;
        } catch (LoginValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LoginValidationException("identifier");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        if (value == null || !value.isTextual()) {
            throw new LoginValidationException(field);
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode body, String field) {
        if (body == null || !body.has(field)) {
            return null;
        }
        return text(body, field);
    }

    @ExceptionHandler(RegistrationCodeOriginException.class)
    public ResponseEntity<ProblemDetail> origin(RegistrationCodeOriginException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "csrf_rejected", exception.getMessage(), request);
    }

    @ExceptionHandler(LoginValidationException.class)
    public ResponseEntity<ProblemDetail> invalid(LoginValidationException exception,
            HttpServletRequest request) {
        var response = problem(HttpStatus.BAD_REQUEST, "validation", exception.getMessage(), request);
        response.getBody().setProperty("fieldErrors", List.of(Map.of("field", exception.field(),
                "code", "validation", "message", exception.getMessage())));
        return response;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> denied(InvalidCredentialsException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "invalid_credentials", exception.getMessage(), request);
    }

    @ExceptionHandler(AccountEmailCodeRateLimitedException.class)
    public ResponseEntity<ProblemDetail> limited(AccountEmailCodeRateLimitedException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", exception.getMessage(), request);
    }

    @ExceptionHandler(RegistrationChallengeException.class)
    public ResponseEntity<ProblemDetail> challenge(RegistrationChallengeException exception,
            HttpServletRequest request) {
        return switch (exception.reason()) {
            case REQUIRED -> problem(HttpStatus.BAD_REQUEST, "challenge_required", exception.getMessage(), request);
            case FAILED -> problem(HttpStatus.FORBIDDEN, "challenge_failed", exception.getMessage(), request);
            case UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "challenge_unavailable",
                    exception.getMessage(), request);
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "请输入有效的登录信息。", request);
    }

    @ExceptionHandler({LoginUnavailableException.class, DataAccessException.class})
    public ResponseEntity<ProblemDetail> unavailable(RuntimeException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "login_unavailable",
                "登录功能暂不可用，请稍后重试。", request);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof RegistrationCodeOriginException) {
            return "csrf_rejected";
        }
        if (exception instanceof LoginValidationException) {
            return "invalid";
        }
        if (exception instanceof InvalidCredentialsException) {
            return "denied";
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
        if (exception instanceof LoginUnavailableException || exception instanceof DataAccessException) {
            return "unavailable";
        }
        return "failure";
    }

    private static Duration elapsed(long started) { return Duration.ofNanos(System.nanoTime() - started); }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code,
            String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}
