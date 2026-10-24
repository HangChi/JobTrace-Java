package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.BrowserSessionQuery;
import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reads only the current signed browser session; it does not authorize other API routes. */
@RestController
public class BrowserSessionController {
    public static final String PATH = "/api/auth/session";

    private final BetterAuthSessionCookie cookie;
    private final BrowserSessionQuery sessions;

    public BrowserSessionController(BetterAuthSessionCookie cookie, BrowserSessionQuery sessions) {
        this.cookie = cookie;
        this.sessions = sessions;
    }

    @GetMapping(PATH)
    public ResponseEntity<?> current(HttpServletRequest request) {
        try {
            String token = cookie.verifiedToken(request.getHeader(HttpHeaders.COOKIE));
            if (token == null) {
                return problem(HttpStatus.UNAUTHORIZED, "unauthorized", request);
            }
            return sessions.findActive(token)
                    .<ResponseEntity<?>>map(session -> ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                            .body(new CurrentSessionResponse(
                                    new SessionView(session.id(), session.expiresAt()),
                                    new UserView(session.userId(), session.displayName(),
                                            session.image(), session.role()))))
                    .orElseGet(() -> problem(HttpStatus.UNAUTHORIZED, "unauthorized", request));
        } catch (LoginUnavailableException | DataAccessException exception) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "session_unavailable", request);
        }
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status,
                status == HttpStatus.UNAUTHORIZED
                        ? "Authentication is required." : "Session service is unavailable.");
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }

    public record CurrentSessionResponse(SessionView session, UserView user) {}
    public record SessionView(String id, Instant expiresAt) {}
    public record UserView(String id, String displayName, String image, String role) {}
}
