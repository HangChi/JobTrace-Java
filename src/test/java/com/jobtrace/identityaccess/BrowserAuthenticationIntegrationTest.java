package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import com.jobtrace.identityaccess.application.AvatarStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_SECRET=0123456789abcdef0123456789abcdef",
        "BETTER_AUTH_URL=https://jobtrace.test",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class BrowserAuthenticationIntegrationTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private BetterAuthSessionCookie cookie;
    @MockitoBean private AvatarStore avatarStore;

    @BeforeEach
    void sessionSchema() {
        jdbc.execute("alter table users add column role text not null default 'user'");
        jdbc.execute("alter table users add column disabled boolean not null default false");
        jdbc.execute("alter table sessions add column user_agent text");
    }

    @Test
    void signedCookieReachesOwnerSessionsWithoutPublicIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/profile/sessions")
                .header(HttpHeaders.COOKIE, signed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + CURRENT_SESSION
                        + "')].current").value(hasItem(true)));
        mockMvc.perform(get("/api/profile/sessions")
                .header("X-JobTrace-Session-Id", CURRENT_SESSION)
                .header("X-JobTrace-User-Id", OWNER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revocationAndDisablementTakeEffectOnNextRequest() throws Exception {
        mockMvc.perform(get("/api/profile/sessions").header(HttpHeaders.COOKIE, signed()))
                .andExpect(status().isOk());
        jdbc.update("delete from sessions where id=?", CURRENT_SESSION);
        mockMvc.perform(get("/api/profile/sessions").header(HttpHeaders.COOKIE, signed()))
                .andExpect(status().isUnauthorized());
        insertSession(CURRENT_SESSION, OWNER);
        jdbc.update("update users set disabled=true where id=?", OWNER);
        mockMvc.perform(get("/api/profile/sessions").header(HttpHeaders.COOKIE, signed()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserWritesRequireExactOriginAndIgnoreUntrustedSessionHeader() throws Exception {
        String body = "{\"displayName\":\"Browser Owner\"}";
        for (String origin : new String[] {"", "https://other.test"}) {
            var request = patch("/api/profile")
                    .header(HttpHeaders.COOKIE, signed())
                    .contentType(MediaType.APPLICATION_JSON).content(body);
            if (!origin.isEmpty()) {
                request.header(HttpHeaders.ORIGIN, origin);
            }
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select display_name from users where id=?",
                String.class, OWNER)).isEqualTo(OWNER);
        mockMvc.perform(patch("/api/profile")
                .header(HttpHeaders.COOKIE, signed())
                .header(HttpHeaders.ORIGIN, "https://jobtrace.test")
                .header("X-JobTrace-Session-Id", FOREIGN_SESSION)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OWNER));
        assertThat(jdbc.queryForObject("select display_name from users where id=?",
                String.class, OWNER)).isEqualTo("Browser Owner");
    }

    @Test
    void invalidCookieCannotDisableExistingCsrfBoundary() throws Exception {
        String body = "{\"displayName\":\"Forged\"}";
        mockMvc.perform(patch("/api/profile")
                .header(HttpHeaders.COOKIE, "better-auth.session_token=forged")
                .header(HttpHeaders.ORIGIN, "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/profile")
                .header(HttpHeaders.COOKIE, cookie.header("stale-token").split(";", 2)[0])
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select display_name from users where id=?",
                String.class, OWNER)).isEqualTo(OWNER);
    }

    @Test
    void avatarUploadRequiresCurrentOwnerAndTrustedOrigin() throws Exception {
        var png = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        when(avatarStore.upload(any(), eq("image/png"), any()))
                .thenAnswer(invocation -> "https://images.test/" + invocation.getArgument(0));
        mockMvc.perform(multipart("/api/profile/avatar").file(png)
                .header(HttpHeaders.COOKIE, signed()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/profile/avatar").file(png)
                .header(HttpHeaders.COOKIE, signed())
                .header(HttpHeaders.ORIGIN, "https://jobtrace.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers
                        .startsWith("https://images.test/avatars/" + OWNER + "/")));
    }

    private String signed() {
        return cookie.header("token-" + CURRENT_SESSION).split(";", 2)[0];
    }
}
