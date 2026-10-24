package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountSessionReadController;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccountSessionSecurityTest.BridgeTestConfiguration.class)
class AccountSessionRevocationSecurityTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void validDeleteBoundAssertionIsSingleUse() throws Exception {
        String assertion = token("DELETE", AccountSessionReadController.PATH);
        mockMvc.perform(request(assertion, "session-older")).andExpect(status().isOk());
        mockMvc.perform(request(assertion, CURRENT)).andExpect(status().isUnauthorized());
        assertThat(exists(CURRENT)).isTrue();
    }

    @Test
    void rejectsAbsentOrdinaryAndMethodOrPathMismatchedIdentityWithoutDeleting() throws Exception {
        mockMvc.perform(delete(AccountSessionReadController.PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"session-older\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(AccountSessionReadController.PATH)
                        .with(user(OWNER))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"session-older\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("GET", AccountSessionReadController.PATH),
                        "session-older"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("DELETE", "/api/reminders"), "session-older"))
                .andExpect(status().isUnauthorized());
        assertThat(exists("session-older")).isTrue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String assertion, String id) {
        return delete(AccountSessionReadController.PATH)
                .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                .header("Authorization", "JobTraceBridge " + assertion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}")
                .with(csrf());
    }

    private static String token(String method, String path) {
        return BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID,
                BridgeTokenFixtures.CURRENT_SECRET,
                OWNER,
                Map.of("mth", method, "pth", path, "jti", UUID.randomUUID().toString()));
    }

    private boolean exists(String id) {
        return jdbc.queryForObject(
                "select exists(select 1 from sessions where id=?)", Boolean.class, id);
    }
}
