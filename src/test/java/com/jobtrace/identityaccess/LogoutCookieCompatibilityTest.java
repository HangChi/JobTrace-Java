package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogoutCookieCompatibilityTest {

    private final BetterAuthSessionCookie secure = new BetterAuthSessionCookie(
            "0123456789abcdef0123456789abcdef", "https://jobtrace.test", false);

    @Test
    void acceptsInstalledBetterAuthNodeSignatureButNoOtherToken() {
        String signed = secure.header("test-session-token").split(";", 2)[0];
        assertThat(secure.verifiedToken(signed)).isEqualTo("test-session-token");
        assertThat(secure.verifiedToken(signed.replace("test-session-token", "other-session-token")))
                .isNull();
        assertThat(secure.verifiedToken("better-auth.session_token=" + signed.split("=", 2)[1]))
                .isNull();
        assertThat(secure.verifiedToken(signed + "; " + signed)).isNull();
        assertThat(secure.verifiedToken(signed + "; unrelated=value"))
                .isEqualTo("test-session-token");
    }

    @Test
    void malformedAndUnboundedCookieCannotSelectSession() {
        for (String header : new String[] {null, "", "__Secure-better-auth.session_token=%ZZ",
                "__Secure-better-auth.session_token=unsigned",
                "__Secure-better-auth.session_token=token.signature.extra",
                "__Secure-better-auth.session_token=" + "a".repeat(600),
                "__Secure-better-auth.session_token=token.bad%2Bsignature"}) {
            assertThat(secure.verifiedToken(header)).isNull();
        }
    }

    @Test
    void expiresLegacyNamesAndPresentSessionDataChunks() {
        List<String> headers = secure.expiredHeaders("__Secure-better-auth.session_data.0=a; "
                + "__Secure-better-auth.session_data.1=b; unrelated=x");
        assertThat(headers).hasSize(5);
        assertThat(headers).anySatisfy(value -> assertThat(value)
                .startsWith("__Secure-better-auth.session_token=")
                .contains("Max-Age=0", "Path=/", "HttpOnly", "SameSite=Lax", "Secure"));
        assertThat(headers).anySatisfy(value -> assertThat(value)
                .startsWith("__Secure-better-auth.session_data.0="));
        assertThat(headers).anySatisfy(value -> assertThat(value)
                .startsWith("__Secure-better-auth.session_data.1="));
        assertThat(headers).noneMatch(value -> value.startsWith("unrelated="));
    }

    @Test
    void plainHttpUsesPlainNamesWithoutSecureFlag() {
        var cookie = new BetterAuthSessionCookie("secret", "http://localhost", false);
        String signed = cookie.header("plain-token").split(";", 2)[0];
        assertThat(cookie.verifiedToken(signed)).isEqualTo("plain-token");
        assertThat(cookie.expiredHeaders(signed)).allSatisfy(value -> assertThat(value)
                .doesNotContain("__Secure-", "; Secure"));
    }
}
