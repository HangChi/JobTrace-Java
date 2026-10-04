package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "BETTER_AUTH_SECRET=",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class LogoutMissingSecretTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;

    @Test
    void missingSigningSecretFailsBeforeAnyDeletion() throws Exception {
        int before = jdbc.queryForObject("select count(*) from sessions", Integer.class);
        mockMvc.perform(post("/api/auth/logout")
                .header("Origin", "https://jobtrace.test")
                .header("Cookie", "better-auth.session_token=opaque-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("logout_unavailable"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(before);
    }
}
