package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_SECRET=0123456789abcdef0123456789abcdef",
        "BETTER_AUTH_URL=https://jobtrace.test",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class LogoutIntegrationTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private BetterAuthSessionCookie cookie;

    @Test
    void validCookieDeletesOnlyItsSessionAndExpiresBrowserCookies() throws Exception {
        String signed = cookie.header("token-" + CURRENT_SESSION).split(";", 2)[0];
        var response = mockMvc.perform(post("/api/auth/logout")
                .header("Origin", "https://jobtrace.test")
                .header("Cookie", signed + "; __Secure-better-auth.session_data.0=cache"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(4)
                .allSatisfy(value -> assertThat(value).contains("Max-Age=0", "HttpOnly"));
        assertThat(jdbc.queryForObject("select count(*) from sessions where token=?", Integer.class,
                "token-" + CURRENT_SESSION)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sessions where id in (?,?)",
                Integer.class, OTHER_SESSION, FOREIGN_SESSION)).isEqualTo(2);
    }

    @Test
    void absentTamperedDuplicateAndStaleCookiesAreIdempotent() throws Exception {
        String signed = cookie.header("token-" + CURRENT_SESSION).split(";", 2)[0];
        for (String headerValue : new String[] {"", signed.replace("token-" + CURRENT_SESSION,
                "tampered-token"),
                signed + "; " + signed, cookie.header("missing-token").split(";", 2)[0]}) {
            var request = post("/api/auth/logout").header("Origin", "https://jobtrace.test");
            if (!headerValue.isEmpty()) {
                request.header("Cookie", headerValue);
            }
            mockMvc.perform(request).andExpect(status().isNoContent())
                    .andExpect(header().exists(HttpHeaders.SET_COOKIE));
        }
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void crossSiteRequestCannotDeleteOrExpire() throws Exception {
        String signed = cookie.header("token-" + CURRENT_SESSION).split(";", 2)[0];
        mockMvc.perform(post("/api/auth/logout")
                .header("Origin", "https://other.test")
                .header("Cookie", signed))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void unsignedIdentityHeaderCannotSelectSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Origin", "https://jobtrace.test")
                .header("x-jobtrace-user-id", OWNER)
                .header("x-jobtrace-session-token", "token-" + CURRENT_SESSION))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void representativeLogoutP95StaysUnderOneSecond() throws Exception {
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            long started = System.nanoTime();
            mockMvc.perform(post("/api/auth/logout").header("Origin", "https://jobtrace.test"))
                    .andExpect(status().isNoContent());
            samples.add(System.nanoTime() - started);
        }
        Collections.sort(samples);
        assertThat(Duration.ofNanos(samples.get(4))).isLessThan(Duration.ofSeconds(1));
    }
}
