package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.BrowserSessionQuery;
import com.jobtrace.identityaccess.domain.BrowserIdentity;
import com.jobtrace.identityaccess.domain.BrowserSession;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import com.jobtrace.identityaccess.web.BrowserAuthenticationFilter;
import com.jobtrace.identityaccess.web.RegistrationCodeOriginGuard;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class BrowserAuthenticationFilterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final BetterAuthSessionCookie cookie = new BetterAuthSessionCookie(
            SECRET, "https://jobtrace.test", false);
    private final BrowserSessionQuery sessions = mock(BrowserSessionQuery.class);
    private final BrowserAuthenticationFilter filter = new BrowserAuthenticationFilter(cookie,
            sessions, new RegistrationCodeOriginGuard("https://jobtrace.test"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsPublicAndExplicitBridgeRequests() throws Exception {
        for (String path : new String[] {"/", "/api/auth/login", "/api/health",
                "/api/health/live"}) {
            var request = new MockHttpServletRequest("GET", path);
            var chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
        var bridge = request("GET", "/api/profile");
        bridge.addHeader(HttpHeaders.AUTHORIZATION, "JobTraceBridge assertion");
        var chain = new MockFilterChain();
        filter.doFilter(bridge, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(bridge);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingInvalidAndRevokedCookiesDoNotAuthenticate() throws Exception {
        for (String header : new String[] {null, "other=value", signed("unknown")}) {
            var request = new MockHttpServletRequest("GET", "/api/profile");
            if (header != null) {
                request.addHeader(HttpHeaders.COOKIE, header);
            }
            var chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Test
    void trustedSessionAuthenticatesOnlyForDurationOfFilterChain() throws Exception {
        when(sessions.findActive("good")).thenReturn(Optional.of(session("user")));
        var request = request("GET", "/api/profile");
        BrowserIdentity[] seen = new BrowserIdentity[1];
        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) ->
                seen[0] = (BrowserIdentity) SecurityContextHolder.getContext()
                        .getAuthentication().getDetails());
        assertThat(seen[0]).isEqualTo(new BrowserIdentity("owner", "session-a",
                BrowserIdentity.Role.USER));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unsafeWritesRequireExactOriginBeforeAnyControllerRuns() throws Exception {
        when(sessions.findActive("good")).thenReturn(Optional.of(session("admin")));
        for (String origin : new String[] {null, "https://other.test"}) {
            var request = request("PATCH", "/api/profile");
            if (origin != null) {
                request.addHeader(HttpHeaders.ORIGIN, origin);
            }
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
            assertThat(chain.getRequest()).isNull();
        }
        var allowed = request("PATCH", "/api/profile");
        allowed.addHeader(HttpHeaders.ORIGIN, "https://jobtrace.test");
        BrowserIdentity[] seen = new BrowserIdentity[1];
        filter.doFilter(allowed, new MockHttpServletResponse(), (incoming, outgoing) ->
                seen[0] = (BrowserIdentity) SecurityContextHolder.getContext()
                        .getAuthentication().getDetails());
        assertThat(seen[0].role()).isEqualTo(BrowserIdentity.Role.ADMIN);
    }

    @Test
    void unavailableStoreSecretAndInvalidRoleFailClosed() throws Exception {
        when(sessions.findActive("good"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/profile"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);

        var noSecret = new BrowserAuthenticationFilter(new BetterAuthSessionCookie(
                "", "https://jobtrace.test", false), sessions,
                new RegistrationCodeOriginGuard("https://jobtrace.test"));
        response = new MockHttpServletResponse();
        noSecret.doFilter(request("GET", "/api/profile"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);

        reset(sessions);
        when(sessions.findActive("good")).thenReturn(Optional.of(session("unknown")));
        response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/profile"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader(HttpHeaders.COOKIE, signed("good"));
        return request;
    }

    private String signed(String token) {
        return cookie.header(token).split(";", 2)[0];
    }

    private static BrowserSession session(String role) {
        return new BrowserSession("session-a", Instant.now().plusSeconds(60),
                "owner", "Owner", null, role);
    }
}
