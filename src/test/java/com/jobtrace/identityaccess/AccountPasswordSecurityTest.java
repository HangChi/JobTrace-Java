package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountPasswordController;
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
class AccountPasswordSecurityTest extends AccountPasswordDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void validPostBoundAssertionIsSingleUse() throws Exception {
        String assertion = token("POST", AccountPasswordController.PATH);
        mockMvc.perform(request(assertion)).andExpect(status().isOk());
        mockMvc.perform(request(assertion)).andExpect(status().isUnauthorized());
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION);
    }

    @Test
    void absentOrdinaryAndMismatchedAssertionsNeverChangeCredential() throws Exception {
        mockMvc.perform(baseRequest())
                .andExpect(status().isUnauthorized());
        mockMvc.perform(baseRequest().with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("GET", AccountPasswordController.PATH)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("POST", "/api/profile")))
                .andExpect(status().isUnauthorized());
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String assertion) {
        return baseRequest()
                .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                .header("Authorization", "JobTraceBridge " + assertion);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder baseRequest() {
        return post(AccountPasswordController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT_SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Current-Password\","
                        + "\"newPassword\":\"New-Password\"}")
                .with(csrf());
    }

    private static String token(String method, String path) {
        return BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID,
                BridgeTokenFixtures.CURRENT_SECRET,
                OWNER,
                Map.of("mth", method, "pth", path,
                        "jti", UUID.randomUUID().toString()));
    }
}
