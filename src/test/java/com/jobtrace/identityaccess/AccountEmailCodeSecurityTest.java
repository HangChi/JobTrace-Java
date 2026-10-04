package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.web.AccountEmailCodeController;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccountSessionSecurityTest.BridgeTestConfiguration.class)
class AccountEmailCodeSecurityTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void postAssertionIsSingleUse() throws Exception {
        String assertion = token("POST", AccountEmailCodeController.PATH);
        mockMvc.perform(request(assertion)).andExpect(status().isAccepted());
        mockMvc.perform(request(assertion)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email='fresh@example.test' and user_id=?
                """, Integer.class, OWNER)).isEqualTo(1);
    }

    @Test
    void absentOrdinaryAndMismatchedAssertionsCannotIssue() throws Exception {
        mockMvc.perform(baseRequest()).andExpect(status().isUnauthorized());
        mockMvc.perform(baseRequest().with(user(OWNER))).andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("PATCH", AccountEmailCodeController.PATH)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(token("POST", "/api/profile/email")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email='fresh@example.test' and user_id=?
                """, Integer.class, OWNER)).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String assertion) {
        return baseRequest()
                .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                .header("Authorization", "JobTraceBridge " + assertion);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder baseRequest() {
        return post(AccountEmailCodeController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT_SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fresh@example.test\"}")
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
