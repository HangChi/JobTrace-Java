package com.jobtrace.reminders.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.reminders.application.GetReminderSummary;
import com.jobtrace.reminders.application.GetReminderPreferences;
import com.jobtrace.reminders.domain.ReminderPreferences;
import com.jobtrace.reminders.domain.ReminderSelection;
import com.jobtrace.reminders.domain.ReminderSummary;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReminderReadController {

    private final GetReminderSummary getSummary;
    private final GetReminderPreferences getPreferences;
    private final ReminderReadMetrics metrics;

    public ReminderReadController(GetReminderSummary getSummary,
            GetReminderPreferences getPreferences, ReminderReadMetrics metrics) {
        this.getSummary = getSummary;
        this.getPreferences = getPreferences;
        this.metrics = metrics;
    }

    @GetMapping("/api/reminder-settings")
    public ResponseEntity<ReminderPreferences> preferences(Principal principal) {
        long started = System.nanoTime();
        try {
            var preferences = getPreferences.execute(BridgePrincipalOwner.require(principal));
            metrics.record("preferences", "success", elapsed(started));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(preferences);
        } catch (RuntimeException exception) {
            metrics.record("preferences", outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @GetMapping("/api/reminders")
    public ResponseEntity<ReminderSummary> summary(
            @RequestParam(required = false) String status, Principal principal) {
        long started = System.nanoTime();
        try {
            var summary = getSummary.execute(
                    BridgePrincipalOwner.require(principal), ReminderSelection.from(status));
            metrics.record("summary", "success", elapsed(started));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(summary);
        } catch (RuntimeException exception) {
            metrics.record("summary", outcome(exception), elapsed(started));
            throw exception;
        }
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem problem && problem.status() == HttpStatus.UNAUTHORIZED) {
            return "denied_identity";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }
}
