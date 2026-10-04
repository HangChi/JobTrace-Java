package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.LogoutSession;
import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Enumeration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public sign-out command; only a valid signed cookie can select one row for deletion. */
@RestController
public class LogoutController {
    public static final String PATH = "/api/auth/logout";

    private final RegistrationCodeOriginGuard origin;
    private final BetterAuthSessionCookie cookies;
    private final LogoutSession logout;
    private final LogoutMetrics metrics;

    public LogoutController(RegistrationCodeOriginGuard origin, BetterAuthSessionCookie cookies,
            LogoutSession logout, LogoutMetrics metrics) {
        this.origin = origin;
        this.cookies = cookies;
        this.logout = logout;
        this.metrics = metrics;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        long started = System.nanoTime();
        try {
            origin.assertAllowed(request);
            cookies.ensureAvailable();
            Enumeration<String> headers = request.getHeaders(HttpHeaders.COOKIE);
            String raw = headers.hasMoreElements() ? headers.nextElement() : null;
            String token = headers.hasMoreElements() ? null : cookies.verifiedToken(raw);
            String outcome = "no_session";
            if (token != null) {
                try {
                    outcome = logout.execute(token) ? "deleted" : "no_session";
                } catch (DataAccessException exception) {
                    // Preserve legacy best-effort sign-out; do not expose the token in diagnostics.
                    outcome = "delete_failure";
                }
            }
            metrics.record(outcome, elapsed(started));
            return ResponseEntity.noContent()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header(HttpHeaders.SET_COOKIE,
                            cookies.expiredHeaders(raw).toArray(String[]::new))
                    .build();
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(RegistrationCodeOriginException.class)
    public ResponseEntity<ProblemDetail> origin(RegistrationCodeOriginException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "csrf_rejected", exception.getMessage(), request);
    }

    @ExceptionHandler(LoginUnavailableException.class)
    public ResponseEntity<ProblemDetail> unavailable(LoginUnavailableException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "logout_unavailable",
                "登出功能暂不可用，请稍后重试。", request);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof RegistrationCodeOriginException) {
            return "csrf_rejected";
        }
        return exception instanceof LoginUnavailableException ? "unavailable" : "failure";
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
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }
}
