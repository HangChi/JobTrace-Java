package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.BrowserSessionQuery;
import com.jobtrace.identityaccess.domain.BrowserIdentity;
import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.domain.RegistrationCodeOriginException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates ambient browser cookies separately from explicit bridge assertions. */
@Component
public final class BrowserAuthenticationFilter extends OncePerRequestFilter {
    private final BetterAuthSessionCookie cookie;
    private final BrowserSessionQuery sessions;
    private final RegistrationCodeOriginGuard origin;

    public BrowserAuthenticationFilter(BetterAuthSessionCookie cookie,
            BrowserSessionQuery sessions, RegistrationCodeOriginGuard origin) {
        this.cookie = cookie;
        this.sessions = sessions;
        this.origin = origin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/")
                || "/api/health".equals(path) || path.startsWith("/api/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("JobTraceBridge ")) {
            chain.doFilter(request, response);
            return;
        }
        String rawCookie = request.getHeader(HttpHeaders.COOKIE);
        if (rawCookie == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String token = cookie.verifiedToken(rawCookie);
            if (token == null) {
                chain.doFilter(request, response);
                return;
            }
            if (!safeMethod(request.getMethod())) {
                if (request.getHeader(HttpHeaders.ORIGIN) == null) {
                    reject(response, HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                try {
                    origin.assertAllowed(request);
                } catch (RegistrationCodeOriginException exception) {
                    reject(response, HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
            var current = sessions.findActive(token);
            if (current.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }
            var session = current.get();
            BrowserIdentity identity = new BrowserIdentity(session.userId(), session.id(),
                    BrowserIdentity.Role.fromDatabase(session.role()));
            var authority = new SimpleGrantedAuthority("ROLE_" + identity.role().name());
            var authentication = new UsernamePasswordAuthenticationToken(identity.subject(), null,
                    List.of(authority));
            authentication.setDetails(identity);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        } catch (LoginUnavailableException | DataAccessException
                | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    private static boolean safeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method)
                || "OPTIONS".equals(method) || "TRACE".equals(method);
    }

    private static void reject(HttpServletResponse response, int status) {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    }
}
