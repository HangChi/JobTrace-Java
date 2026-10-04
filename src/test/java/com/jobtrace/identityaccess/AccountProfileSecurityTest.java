package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountProfileController;
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
class AccountProfileSecurityTest extends AccountProfileDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void validPatchBoundAssertionIsSingleUse() throws Exception {
        String assertion = token("PATCH", AccountProfileController.PATH);
        mockMvc.perform(request(assertion, "New Name")).andExpect(status().isOk());
        mockMvc.perform(request(assertion, "Replay Name")).andExpect(status().isUnauthorized());
        assertThat(name()).isEqualTo("New Name");
    }

    @Test
    void absentOrdinaryAndMismatchedAssertionsNeverUpdate() throws Exception {
        mockMvc.perform(patch(AccountProfileController.PATH)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Attack\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(AccountProfileController.PATH)
                        .with(user(OWNER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Attack\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("GET", AccountProfileController.PATH), "Attack"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("PATCH", "/api/profile/password"), "Attack"))
                .andExpect(status().isUnauthorized());
        assertThat(name()).isEqualTo("Old Name");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String assertion, String name) {
        return patch(AccountProfileController.PATH)
                .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                .header("Authorization", "JobTraceBridge " + assertion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + name + "\"}")
                .with(csrf());
    }

    private static String token(String method, String path) {
        return BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.CURRENT_SECRET,
                OWNER, Map.of("mth", method, "pth", path,
                        "jti", UUID.randomUUID().toString()));
    }

    private String name() {
        return jdbc.queryForObject(
                "select display_name from users where id=?", String.class, OWNER);
    }
}
