package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import org.junit.jupiter.api.Test;

class LoginCookieCompatibilityTest {
    @Test
    void matchesInstalledBetterAuthNodeSignatureVector() {
        var cookie = new BetterAuthSessionCookie("0123456789abcdef0123456789abcdef",
                "https://jobtrace.test", false);
        assertThat(cookie.header("test-session-token")).isEqualTo(
                "__Secure-better-auth.session_token="
                + "test-session-token.rwqDdH59kVgflWF2PMn%2B0t3ZqvYil5o0FkdpSzdQMvg%3D"
                + "; Max-Age=604800; Path=/; HttpOnly; SameSite=Lax; Secure");
    }

    @Test
    void missingSecretFailsClosedAndPlainHttpOmitsSecurePrefix() {
        assertThatThrownBy(() -> new BetterAuthSessionCookie("", "", false).ensureAvailable())
                .isInstanceOf(LoginUnavailableException.class);
        assertThat(new BetterAuthSessionCookie("secret", "http://localhost", false)
                .header("token")).startsWith("better-auth.session_token=")
                .doesNotContain("; Secure");
    }
}
