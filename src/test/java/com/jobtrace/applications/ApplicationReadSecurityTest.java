package com.jobtrace.applications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.BridgeTokenFixtures;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.shared.health.BridgeReplayAvailability;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
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
@Import(ApplicationReadSecurityTest.BridgeTestConfiguration.class)
class ApplicationReadSecurityTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void bothRoutesRejectPublicHeadersAndForgedStaleOrPathMismatchedAssertions() throws Exception {
        for (String path : new String[] {"/api/applications", "/api/applications/" + FIRST_ID}) {
            mockMvc.perform(get(path).header("x-user-id", OWNER))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + token(path, Map.of(
                                    "exp", Date.from(BridgeTokenFixtures.NOW.minusSeconds(1))))))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + token(
                                    "/api/analytics/summary", Map.of())))
                    .andExpect(status().isUnauthorized());
            String forged = BridgeTokenFixtures.token(
                    BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.PREVIOUS_SECRET,
                    OWNER, Map.of("pth", path, "jti", UUID.randomUUID().toString()));
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + forged))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void signedOwnerIsolatedAndReplayedAssertionIsRejected() throws Exception {
        String assertion = token("/api/applications", Map.of());
        mockMvc.perform(get("/api/applications")
                        .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                        .header("Authorization", "JobTraceBridge " + assertion)
                        .header("x-user-id", OTHER_OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/applications")
                        .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                        .header("Authorization", "JobTraceBridge " + assertion))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/applications/{id}", OTHER_ID)
                        .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                        .header("Authorization", "JobTraceBridge " + token(
                                "/api/applications/" + OTHER_ID, Map.of())))
                .andExpect(status().isNotFound());

        String detailAssertion = token("/api/applications/" + FIRST_ID, Map.of());
        mockMvc.perform(get("/api/applications/{id}", FIRST_ID)
                        .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                        .header("Authorization", "JobTraceBridge " + detailAssertion))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/applications/{id}", FIRST_ID)
                        .header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                        .header("Authorization", "JobTraceBridge " + detailAssertion))
                .andExpect(status().isUnauthorized());
    }

    private static String token(String path, Map<String, Object> overrides) {
        var claims = new java.util.HashMap<String, Object>(overrides);
        claims.put("pth", path);
        claims.put("jti", UUID.randomUUID().toString());
        return BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.CURRENT_SECRET,
                OWNER, claims);
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

    private static final class InMemoryReplayGuard implements ReplayGuard, BridgeReplayAvailability {
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
