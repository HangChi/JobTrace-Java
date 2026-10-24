package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.application.PasswordResetDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_URL=https://jobtrace.test",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class PasswordRecoveryHttpIntegrationTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordHasher hasher;
    @MockitoBean private PasswordResetDelivery delivery;

    @BeforeEach
    void recoverySchema() {
        jdbc.execute("drop table if exists verification_tokens");
        jdbc.execute("""
                create table verification_tokens (
                  id text primary key,identifier text not null,value text not null,
                  expires_at timestamptz not null,created_at timestamptz not null default now(),
                  updated_at timestamptz not null default now())
                """);
        jdbc.execute("alter table users add column role text not null default 'user'");
        jdbc.execute("alter table users add column disabled boolean not null default false");
    }

    @Test
    void knownAndUnknownHaveSameResponseAndOneUseRevokesSessions() throws Exception {
        String known = request(OWNER_EMAIL);
        String unknown = request("missing@example.test");
        assertThat(known).isEqualTo(unknown);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq(OWNER_EMAIL), link.capture());
        String token = link.getValue().split("/reset-password/")[1].split("\\?")[0];
        mockMvc.perform(get("/api/auth/reset-password/" + token)
                .param("callbackURL", "/reset-password"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/reset-password?token=" + token));
        String body = "{\"token\":\"" + token + "\",\"newPassword\":\"New-Password\"}";
        mockMvc.perform(post("/api/auth/reset-password")
                .header(HttpHeaders.ORIGIN, "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
        assertThat(hasher.matches("New-Password", ownerHash())).isTrue();
        assertThat(ownerSessions()).isEmpty();
        assertThat(jdbc.queryForList("select id from sessions where user_id=?",
                String.class, OTHER_OWNER)).containsExactly(FOREIGN_SESSION);
        mockMvc.perform(post("/api/auth/reset-password")
                .header(HttpHeaders.ORIGIN, "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_reset_token"));
    }

    private String request(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/request-password-reset")
                .header(HttpHeaders.ORIGIN, "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
