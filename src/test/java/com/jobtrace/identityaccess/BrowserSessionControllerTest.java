package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.BrowserSessionQuery;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import com.jobtrace.identityaccess.web.BrowserSessionController;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class BrowserSessionControllerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void missingSigningSecretAndStoreFailureReturnSafeUnavailable() {
        BrowserSessionQuery sessions = mock(BrowserSessionQuery.class);
        BetterAuthSessionCookie validCookie = new BetterAuthSessionCookie(
                SECRET, "https://jobtrace.test", false);
        var request = new MockHttpServletRequest("GET", BrowserSessionController.PATH);
        request.addHeader(HttpHeaders.COOKIE, validCookie.header("good").split(";", 2)[0]);

        var missingSecret = new BrowserSessionController(new BetterAuthSessionCookie(
                "", "https://jobtrace.test", false), sessions).current(request);
        assertThat(missingSecret.getStatusCode().value()).isEqualTo(503);
        assertThat(missingSecret.getHeaders().getCacheControl()).isEqualTo("private, no-store");

        when(sessions.findActive("good"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var databaseFailure = new BrowserSessionController(validCookie, sessions).current(request);
        assertThat(databaseFailure.getStatusCode().value()).isEqualTo(503);
        assertThat(databaseFailure.getBody().toString()).doesNotContain("good");
    }

    @Test
    void malformedOrAbsentCookieNeverReachesSessionStore() {
        BrowserSessionQuery sessions = mock(BrowserSessionQuery.class);
        var controller = new BrowserSessionController(new BetterAuthSessionCookie(
                SECRET, "https://jobtrace.test", false), sessions);
        var request = new MockHttpServletRequest("GET", BrowserSessionController.PATH);
        assertThat(controller.current(request).getStatusCode().value()).isEqualTo(401);
        request.addHeader(HttpHeaders.COOKIE, "better-auth.session_token=forged");
        assertThat(controller.current(request).getStatusCode().value()).isEqualTo(401);
    }
}
