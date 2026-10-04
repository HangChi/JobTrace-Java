package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.RequestPasswordReset;
import com.jobtrace.identityaccess.application.RequestPasswordReset.RecoveryUnavailableException;
import com.jobtrace.identityaccess.application.ResetPassword;
import com.jobtrace.identityaccess.application.ResetPassword.InvalidResetTokenException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.infrastructure.HttpPasswordResetDelivery.DeliveryUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class PasswordRecoveryController {
    private final RequestPasswordReset requestReset;
    private final ResetPassword reset;
    private final RegistrationCodeOriginGuard origin;

    public PasswordRecoveryController(RequestPasswordReset requestReset, ResetPassword reset,
            RegistrationCodeOriginGuard origin) {
        this.requestReset = requestReset;
        this.reset = reset;
        this.origin = origin;
    }

    @PostMapping("/api/auth/request-password-reset")
    public ResponseEntity<Map<String, String>> request(@RequestBody Map<String, String> body,
            HttpServletRequest servletRequest) {
        origin.assertAllowed(servletRequest);
        requestReset.execute(body == null ? null : body.get("email"),
                servletRequest.getRemoteAddr());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(Map.of("message", "如果该邮箱存在，我们已发送密码重置说明。"));
    }

    @GetMapping("/api/auth/reset-password/{token}")
    public ResponseEntity<Void> callback(@PathVariable String token,
            @RequestParam(defaultValue = "/reset-password") String callbackURL) {
        if (!"/reset-password".equals(callbackURL)) {
            return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL,
                    "private, no-store").build();
        }
        String destination = reset.valid(token)
                ? "/reset-password?token=" + token
                : "/reset-password?error=INVALID_TOKEN";
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }

    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<Map<String, Boolean>> reset(@RequestBody Map<String, String> body,
            HttpServletRequest servletRequest) {
        origin.assertAllowed(servletRequest);
        reset.execute(body == null ? null : body.get("token"),
                body == null ? null : body.get("newPassword"));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(Map.of("status", true));
    }

    @ExceptionHandler({InvalidResetTokenException.class, AccountPasswordValidationException.class,
            AccountEmailCodeValidationException.class, RegistrationCodeOriginException.class,
            AccountEmailCodeRateLimitedException.class, DeliveryUnavailableException.class,
            RecoveryUnavailableException.class})
    public ResponseEntity<ProblemDetail> failure(RuntimeException exception) {
        HttpStatus status = exception instanceof AccountEmailCodeRateLimitedException
                ? HttpStatus.TOO_MANY_REQUESTS
                : exception instanceof DeliveryUnavailableException
                || exception instanceof RecoveryUnavailableException
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        String code = exception instanceof InvalidResetTokenException
                ? "invalid_reset_token" : exception instanceof AccountEmailCodeRateLimitedException
                ? "rate_limited" : exception instanceof DeliveryUnavailableException
                ? "delivery_unavailable" : exception instanceof RecoveryUnavailableException
                ? "recovery_unavailable" : "validation";
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("urn:jobtrace:problem:" + code));
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(problem);
    }
}
