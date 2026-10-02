package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.AssertionVerifier.VerifiedAssertion;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.shared.security.BridgeAuthenticationEntryPoint;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Converts a fully consumed bridge assertion into the Spring Security principal. */
@Component
@ConditionalOnProperty(name = "jobtrace.auth-bridge.enabled", havingValue = "true")
public final class BridgeAuthenticationFilter extends OncePerRequestFilter {

    public static final String PROTECTED_PATH = "/api/analytics/summary";
    private static final String SCHEME = "JobTraceBridge ";
    private static final String UUID_PATH =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern APPLICATION_DETAIL = Pattern.compile(
            "/api/applications/" + UUID_PATH + "(?:/detail)?");
    private static final Pattern INTERVIEW_DETAIL = Pattern.compile(
            "/api/interviews/" + UUID_PATH);

    private final ClaimAssertionUseCase claimAssertion;
    private final BridgeAuthenticationEntryPoint entryPoint;
    private final BridgeAuthenticationMetrics metrics;

    public BridgeAuthenticationFilter(
            ClaimAssertionUseCase claimAssertion,
            BridgeAuthenticationEntryPoint entryPoint,
            BridgeAuthenticationMetrics metrics) {
        this.claimAssertion = claimAssertion;
        this.entryPoint = entryPoint;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !PROTECTED_PATH.equals(path)
                && !"/api/applications".equals(path)
                && !APPLICATION_DETAIL.matcher(path).matches()
                && !"/api/interviews".equals(path)
                && !INTERVIEW_DETAIL.matcher(path).matches()
                && !("GET".equals(request.getMethod())
                    && ("/api/reminders".equals(path)
                        || "/api/reminder-settings".equals(path)));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Instant startedAt = Instant.now();
        try {
            String assertion = assertion(request.getHeader("Authorization"));
            VerifiedAssertion verified = claimAssertion.execute(
                    assertion,
                    request.getMethod(),
                    request.getRequestURI(),
                    requestId(request));
            var authority = new SimpleGrantedAuthority(
                    "ROLE_" + verified.identity().role().name());
            var authentication = new UsernamePasswordAuthenticationToken(
                    verified.identity().subject(), null, List.of(authority));
            authentication.setDetails(verified.identity());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            metrics.success(Duration.between(startedAt, Instant.now()));
            filterChain.doFilter(request, response);
        } catch (BridgeAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            metrics.failure(
                    exception.failure(),
                    Duration.between(startedAt, Instant.now()));
            entryPoint.commence(request, response, exception.failure().name());
        }
    }

    private static String assertion(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BridgeAuthenticationException(BridgeAuthenticationFailure.ABSENT);
        }
        if (!authorization.startsWith(SCHEME)
                || authorization.length() == SCHEME.length()
                || authorization.indexOf(' ', SCHEME.length()) >= 0) {
            throw new BridgeAuthenticationException(BridgeAuthenticationFailure.MALFORMED);
        }
        return authorization.substring(SCHEME.length());
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value) {
            return value;
        }
        throw new BridgeAuthenticationException(BridgeAuthenticationFailure.REQUEST_MISMATCH);
    }
}
