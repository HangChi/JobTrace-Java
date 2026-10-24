package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.ListAccountSessions;
import com.jobtrace.identityaccess.domain.AccountSession;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountSessionReadController {

    public static final String PATH = "/api/profile/sessions";
    public static final String CURRENT_SESSION_HEADER = "X-JobTrace-Session-Id";

    private final ListAccountSessions listSessions;
    private final AccountSessionReadMetrics metrics;

    public AccountSessionReadController(
            ListAccountSessions listSessions,
            AccountSessionReadMetrics metrics) {
        this.listSessions = listSessions;
        this.metrics = metrics;
    }

    @GetMapping(PATH)
    public ResponseEntity<List<AccountSession>> list(
            @RequestHeader(value = CURRENT_SESSION_HEADER, required = false) String currentSessionId,
            Principal principal) {
        long started = System.nanoTime();
        try {
            var result = listSessions.execute(
                    BridgePrincipalOwner.require(principal),
                    BridgePrincipalOwner.currentSessionId(principal, currentSessionId));
            metrics.record("success", elapsed(started));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(result);
        } catch (RuntimeException exception) {
            metrics.record(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem) {
            return "denied_identity";
        }
        if (exception instanceof IllegalArgumentException) {
            return "invalid";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }
}
