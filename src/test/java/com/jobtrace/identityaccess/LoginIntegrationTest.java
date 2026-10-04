package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.LoginAccount;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.infrastructure.PostgresEmailCodeRateLimiter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_SECRET=0123456789abcdef0123456789abcdef",
        "BETTER_AUTH_URL=https://jobtrace.test",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class LoginIntegrationTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private LoginAccount login;
    @Autowired private PostgresEmailCodeRateLimiter limiter;

    @BeforeEach
    void loginSchema() {
        jdbc.execute("alter table users add column username text unique");
        jdbc.execute("alter table users add column role text not null default 'user'");
        jdbc.execute("alter table users add column disabled boolean not null default false");
        jdbc.execute("alter table sessions add column user_agent text");
        jdbc.update("update users set username='owner', role='admin' where id=?", OWNER);
    }

    @Test
    void usernameAndVerifiedEmailCreateLegacySessionsAndSignedCookies() throws Exception {
        var first = mockMvc.perform(request("""
                {"identifier":"OWNER","password":"Current-Password","returnTo":"/jobs?x=1"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTarget").value("/jobs?x=1"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse();
        assertThat(first.getHeader("Set-Cookie"))
                .startsWith("__Secure-better-auth.session_token=")
                .contains("HttpOnly", "SameSite=Lax", "Secure");
        var second = mockMvc.perform(request("""
                {"identifier":"OWNER-RECOVERY@EXAMPLE.TEST","password":"Current-Password"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTarget").value("/admin"))
                .andReturn().getResponse();
        assertThat(second.getHeader("Set-Cookie")).contains("__Secure-better-auth.session_token=");
        assertThat(jdbc.queryForObject("select count(*) from sessions where user_id=?",
                Integer.class, OWNER)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from sessions where user_agent is not null",
                Integer.class)).isEqualTo(0);
    }

    @Test
    void allCredentialDenialsAreIndistinguishableAndCreateNoSession() throws Exception {
        int before = jdbc.queryForObject("select count(*) from sessions", Integer.class);
        for (String identifier : new String[] {"unknown", "owner", OWNER_EMAIL}) {
            if (OWNER_EMAIL.equals(identifier)) {
                jdbc.update("update users set recovery_email_verified_at=null where id=?", OWNER);
            }
            String password = "owner".equals(identifier) ? "wrong" : CURRENT_PASSWORD;
            mockMvc.perform(request("{\"identifier\":\"" + identifier
                    + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("invalid_credentials"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        jdbc.update("update users set disabled=true where id=?", OWNER);
        mockMvc.perform(request("""
                {"identifier":"owner","password":"Current-Password"}
                """)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void originValidationAndSourceLimitPreventLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .header("Origin", "https://other.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"owner\",\"password\":\"Current-Password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_rejected"));
        for (int i = 0; i < 10; i++) {
            limiter.consumeLoginAttempt();
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(limiter::consumeLoginAttempt)
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
        mockMvc.perform(request("""
                {"identifier":"owner","password":"Current-Password"}
                """)).andExpect(status().isTooManyRequests());
    }

    @Test
    void representativeSuccessfulUseCaseP95StaysBelowOneSecond() {
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            login.execute(new LoginAccount.Input("owner", CURRENT_PASSWORD, null, "browser"));
            samples.add(System.nanoTime() - start);
        }
        Collections.sort(samples);
        assertThat(Duration.ofNanos(samples.get(4))).isLessThan(Duration.ofSeconds(1));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/api/auth/login").header("Origin", "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
