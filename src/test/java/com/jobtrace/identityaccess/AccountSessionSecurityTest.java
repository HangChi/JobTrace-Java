package com.jobtrace.identityaccess;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.identityaccess.web.AccountSessionReadController;
import com.jobtrace.shared.health.BridgeReplayAvailability;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccountSessionSecurityTest.BridgeTestConfiguration.class)
class AccountSessionSecurityTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void rejectsPublicHeadersOrdinaryPrincipalAndBadAssertions() throws Exception {
        mockMvc.perform(get(AccountSessionReadController.PATH).header("x-user-id", OWNER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(AccountSessionReadController.PATH).with(user(OWNER)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(bridgeRequest(token(Map.of(
                        "exp", Date.from(BridgeTokenFixtures.NOW.minusSeconds(1))))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(bridgeRequest(BridgeTokenFixtures.token(
                        BridgeTokenFixtures.CURRENT_KEY_ID,
                        BridgeTokenFixtures.PREVIOUS_SECRET,
                        OWNER,
                        Map.of("pth", AccountSessionReadController.PATH,
                                "jti", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(bridgeRequest(BridgeTokenFixtures.token(
                        BridgeTokenFixtures.CURRENT_KEY_ID,
                        BridgeTokenFixtures.CURRENT_SECRET,
                        OWNER,
                        Map.of("pth", "/api/reminders",
                                "jti", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequestBoundAssertionIsSingleUse() throws Exception {
        String assertion = token(Map.of());
        mockMvc.perform(bridgeRequest(assertion)).andExpect(status().isOk());
        mockMvc.perform(bridgeRequest(assertion)).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            bridgeRequest(String assertion) {
        return get(AccountSessionReadController.PATH)
                .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT)
                .header("Authorization", "JobTraceBridge " + assertion);
    }

    private static String token(Map<String, Object> overrides) {
        var claims = new HashMap<String, Object>(overrides);
        claims.put("pth", AccountSessionReadController.PATH);
        claims.put("jti", UUID.randomUUID().toString());
        return BridgeTokenFixtures.token(BridgeTokenFixtures.CURRENT_KEY_ID,
                BridgeTokenFixtures.CURRENT_SECRET, OWNER, claims);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BridgeTestConfiguration {
        @Bean
        @Primary
        Clock bridgeClock() {
            return Clock.fixed(BridgeTokenFixtures.NOW, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        ReplayGuard replayGuard() {
            return new InMemoryReplayGuard();
        }
    }

    private static final class InMemoryReplayGuard
            implements ReplayGuard, BridgeReplayAvailability {

        private final Set<String> claimed = new HashSet<>();

        @Override
        public boolean claim(String issuer, String tokenId, Instant retainUntil) {
            return claimed.add(issuer + ":" + tokenId);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
