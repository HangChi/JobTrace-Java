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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_SECRET=",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class LoginMissingSecretTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;

    @Test
    void signingConfigurationFailsBeforeSessionCreation() throws Exception {
        int before = jdbc.queryForObject("select count(*) from sessions", Integer.class);
        mockMvc.perform(post("/api/auth/login")
                .header("Origin", "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"owner\",\"password\":\"pass\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("login_unavailable"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(before);
    }
}
