package com.jobtrace.admin.web;

import com.jobtrace.admin.application.GetAdminSummary;
import com.jobtrace.admin.application.GetManagedUserDetail;
import com.jobtrace.admin.application.ListAdminAuditEvents;
import com.jobtrace.admin.application.ListManagedUsers;
import com.jobtrace.admin.application.ChangeManagedUserAccess;
import com.jobtrace.admin.domain.AdminAccessConflictException;
import com.jobtrace.admin.domain.AdminAccessResponse;
import com.jobtrace.admin.domain.AdminAuditEvent;
import com.jobtrace.admin.domain.AdminSummary;
import com.jobtrace.admin.domain.AdminUser;
import com.jobtrace.admin.domain.AdminUserNotFoundException;
import com.jobtrace.admin.domain.CurrentAdminRequiredException;
import com.jobtrace.admin.domain.InvalidAdminAccessCommandException;
import com.jobtrace.admin.domain.InvalidAdminUserIdException;
import com.jobtrace.admin.domain.PageResult;
import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import com.jobtrace.shared.web.Problem;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminReadController {

    private final GetAdminSummary getSummary;
    private final ListManagedUsers listUsers;
    private final GetManagedUserDetail getUserDetail;
    private final ListAdminAuditEvents listAuditEvents;
    private final ChangeManagedUserAccess changeUserAccess;
    private final AdminReadMetrics metrics;

    public AdminReadController(
            GetAdminSummary getSummary,
            ListManagedUsers listUsers,
            GetManagedUserDetail getUserDetail,
            ListAdminAuditEvents listAuditEvents,
            ChangeManagedUserAccess changeUserAccess,
            AdminReadMetrics metrics) {
        this.getSummary = getSummary;
        this.listUsers = listUsers;
        this.getUserDetail = getUserDetail;
        this.listAuditEvents = listAuditEvents;
        this.changeUserAccess = changeUserAccess;
        this.metrics = metrics;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminSummary> summary(Principal principal) {
        return execute("summary", () -> {
            BridgePrincipalAdmin.require(principal);
            return getSummary.execute();
        });
    }

    @GetMapping("/users")
    public ResponseEntity<PageResult<AdminUser.Summary>> users(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        return execute("users", () -> {
            BridgePrincipalAdmin.require(principal);
            return listUsers.execute(AdminReadParameters.users(parameters));
        });
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUser.Detail> user(
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> parameters,
            Principal principal) {
        return execute("user_detail", () -> {
            BridgePrincipalAdmin.require(principal);
            return getUserDetail.execute(id, AdminReadParameters.content(parameters));
        });
    }

    @GetMapping("/audit-events")
    public ResponseEntity<PageResult<AdminAuditEvent>> audit(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        return execute("audit", () -> {
            BridgePrincipalAdmin.require(principal);
            return listAuditEvents.execute(AdminReadParameters.audit(parameters));
        });
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<AdminAccessResponse> changeUserAccess(
            @PathVariable String id,
            @RequestBody AdminAccessRequest request,
            Principal principal) {
        long started = System.nanoTime();
        try {
            String actorUserId = BridgePrincipalAdmin.require(principal);
            if (request == null) {
                throw new InvalidAdminAccessCommandException("请求内容不能为空。");
            }
            AdminAccessResponse body = changeUserAccess.execute(
                    actorUserId, id, request.toCommand());
            metrics.recordAccessChange("success", elapsed(started));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
        } catch (RuntimeException exception) {
            metrics.recordAccessChange(outcome(exception), elapsed(started));
            throw exception;
        }
    }

    @ExceptionHandler(InvalidAdminAccessCommandException.class)
    public ResponseEntity<ProblemDetail> invalidCommand(
            InvalidAdminAccessCommandException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", exception.getMessage(), request);
    }

    @ExceptionHandler(AdminAccessConflictException.class)
    public ResponseEntity<ProblemDetail> accessConflict(
            AdminAccessConflictException exception, HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request);
        if (exception.auditEventId() != null) {
            response.getBody().setProperty("auditEventId", exception.auditEventId());
        }
        if (exception.latestState() != null) {
            response.getBody().setProperty("latestAccessState", exception.latestState());
        }
        return response;
    }

    @ExceptionHandler(CurrentAdminRequiredException.class)
    public ResponseEntity<ProblemDetail> currentAdminRequired(
            CurrentAdminRequiredException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidAdminUserIdException.class)
    public ResponseEntity<ProblemDetail> invalidUserId(
            InvalidAdminUserIdException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", exception.getMessage(), request);
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<ProblemDetail> missingUser(
            AdminUserNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), request);
    }

    private <T> ResponseEntity<T> execute(String operation, ReadOperation<T> action) {
        long started = System.nanoTime();
        try {
            T body = action.execute();
            record(operation, "success", started);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
        } catch (RuntimeException exception) {
            record(operation, outcome(exception), started);
            throw exception;
        }
    }

    private void record(String operation, String outcome, long started) {
        metrics.record(operation, outcome, Duration.ofNanos(System.nanoTime() - started));
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem problem) {
            return switch (problem.status()) {
                case UNAUTHORIZED, FORBIDDEN -> "denied_identity";
                case NOT_FOUND -> "not_found";
                case BAD_REQUEST -> "invalid";
                default -> "failure";
            };
        }
        if (exception instanceof IllegalArgumentException) {
            return "invalid";
        }
        if (exception instanceof InvalidAdminUserIdException) {
            return "invalid";
        }
        if (exception instanceof InvalidAdminAccessCommandException) {
            return "invalid";
        }
        if (exception instanceof AdminUserNotFoundException) {
            return "not_found";
        }
        if (exception instanceof AdminAccessConflictException) {
            return "conflict";
        }
        if (exception instanceof CurrentAdminRequiredException) {
            return "denied_identity";
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
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }

    @FunctionalInterface
    private interface ReadOperation<T> {
        T execute();
    }
}
