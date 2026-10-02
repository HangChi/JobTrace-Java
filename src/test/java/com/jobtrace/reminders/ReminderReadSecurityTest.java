package com.jobtrace.reminders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.BridgeTokenFixtures;
import com.jobtrace.identityaccess.domain.ReplayGuard;
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
@Import(ReminderReadSecurityTest.BridgeTestConfiguration.class)
class ReminderReadSecurityTest extends ReminderReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("jobtrace.auth-bridge.enabled", () -> "true");
        registry.add("jobtrace.auth-bridge.keys[0].id", () -> BridgeTokenFixtures.CURRENT_KEY_ID);
        registry.add("jobtrace.auth-bridge.keys[0].secret", () -> BridgeTokenFixtures.CURRENT_SECRET);
    }

    @Test
    void bothReadsRejectPublicHeadersOrdinaryPrincipalAndBadAssertions() throws Exception {
        for (String path : paths()) {
            mockMvc.perform(get(path).header("x-user-id", OWNER))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).with(user(OWNER)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + token(path, Map.of(
                                    "exp", Date.from(BridgeTokenFixtures.NOW.minusSeconds(1))))))
                    .andExpect(status().isUnauthorized());
            String otherPath = path.equals("/api/reminders")
                    ? "/api/reminder-settings" : "/api/reminders";
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + token(otherPath, Map.of())))
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
    void validRequestBoundAssertionsAreSingleUseOnBothReads() throws Exception {
        for (String path : paths()) {
            String assertion = token(path, Map.of());
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + assertion))
                    .andExpect(status().isOk());
            mockMvc.perform(get(path).header("x-request-id", BridgeTokenFixtures.REQUEST_ID)
                            .header("Authorization", "JobTraceBridge " + assertion))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static String[] paths() {
        return new String[] {"/api/reminders", "/api/reminder-settings"};
    }

    private static String token(String path, Map<String, Object> overrides) {
        var claims = new HashMap<String, Object>(overrides);
        claims.put("pth", path);
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
