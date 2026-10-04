package com.jobtrace.identityaccess;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
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
class AccountProfileReadTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private BetterAuthSessionCookie cookie;

    @BeforeEach
    void sessionSchema() {
        jdbc.execute("alter table users add column role text not null default 'user'");
        jdbc.execute("alter table users add column disabled boolean not null default false");
    }

    @Test
    void returnsOnlyTheCurrentOwnersSafeProfile() throws Exception {
        mockMvc.perform(get("/api/profile")
                .header(HttpHeaders.COOKIE, signed("token-" + CURRENT_SESSION)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.id").value(OWNER))
                .andExpect(jsonPath("$.recoveryEmail").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/api/profile")
                .header(HttpHeaders.COOKIE, signed("token-" + FOREIGN_SESSION))
                .header("X-JobTrace-User-Id", OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OTHER_OWNER));
    }

    @Test
    void absentOrRevokedCookieCannotReadProfile() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
        jdbc.update("delete from sessions where id=?", CURRENT_SESSION);
        mockMvc.perform(get("/api/profile")
                .header(HttpHeaders.COOKIE, signed("token-" + CURRENT_SESSION)))
                .andExpect(status().isUnauthorized());
    }

    private String signed(String token) {
        return cookie.header(token).split(";", 2)[0];
    }
}
