package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
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
        "BETTER_AUTH_URL=https://jobtrace.test"})
class BrowserSessionReadTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private BetterAuthSessionCookie cookie;

    @BeforeEach
    void sessionSchema() {
        jdbc.execute("alter table users add column role text not null default 'user'");
        jdbc.execute("alter table users add column disabled boolean not null default false");
        jdbc.update("update users set role='admin',image='https://images.test/avatar.png' where id=?",
                OWNER);
    }

    @Test
    void signedActiveCookieReturnsCurrentAccountWithoutSecrets() throws Exception {
        var response = mockMvc.perform(get("/api/auth/session")
                .header(HttpHeaders.COOKIE, signed("token-" + CURRENT_SESSION)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.session.id").value(CURRENT_SESSION))
                .andExpect(jsonPath("$.user.id").value(OWNER))
                .andExpect(jsonPath("$.user.role").value("admin"))
                .andExpect(jsonPath("$.user.displayName").value(OWNER))
                .andExpect(jsonPath("$.user.image").value("https://images.test/avatar.png"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(
                "token-", "\"password\"", "recovery_email", "recoveryEmail");
    }

    @Test
    void missingTamperedDuplicateAndRevokedCookiesAreDenied() throws Exception {
        String valid = signed("token-" + CURRENT_SESSION);
        for (String value : new String[] {"", valid + "; " + valid,
                valid.replace("token-" + CURRENT_SESSION, "forged-token"),
                signed("unknown-token")}) {
            var request = get("/api/auth/session");
            if (!value.isEmpty()) {
                request.header(HttpHeaders.COOKIE, value);
            }
            mockMvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        }
        jdbc.update("delete from sessions where id=?", CURRENT_SESSION);
        mockMvc.perform(get("/api/auth/session").header(HttpHeaders.COOKIE, valid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expirationAndCurrentDisablementDenyImmediately() throws Exception {
        String valid = signed("token-" + CURRENT_SESSION);
        jdbc.update("update sessions set expires_at=now()-interval '1 second' where id=?",
                CURRENT_SESSION);
        mockMvc.perform(get("/api/auth/session").header(HttpHeaders.COOKIE, valid))
                .andExpect(status().isUnauthorized());
        jdbc.update("update sessions set expires_at=now()+interval '1 day' where id=?",
                CURRENT_SESSION);
        jdbc.update("update users set disabled=true where id=?", OWNER);
        mockMvc.perform(get("/api/auth/session").header(HttpHeaders.COOKIE, valid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void representativeCurrentSessionReadsMeetP95Budget() throws Exception {
        String valid = signed("token-" + CURRENT_SESSION);
        mockMvc.perform(get("/api/auth/session").header(HttpHeaders.COOKIE, valid))
                .andExpect(status().isOk());
        long[] elapsed = new long[20];
        for (int index = 0; index < elapsed.length; index++) {
            long started = System.nanoTime();
            mockMvc.perform(get("/api/auth/session").header(HttpHeaders.COOKIE, valid))
                    .andExpect(status().isOk());
            elapsed[index] = (System.nanoTime() - started) / 1_000_000;
        }
        Arrays.sort(elapsed);
        assertThat(elapsed[18]).isLessThanOrEqualTo(500);
        System.out.printf("022 current-session read p95 ms: %d%n", elapsed[18]);
    }

    private String signed(String token) {
        return cookie.header(token).split(";", 2)[0];
    }
}
