package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import com.jobtrace.identityaccess.BridgeTokenFixtures;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationException;
import com.jobtrace.identityaccess.domain.BridgeAuthenticationFailure;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.identityaccess.web.BridgeTokenVerifier;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ExportSecurityTest extends ExportDatabaseTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void callerHeadersAndOrdinaryPrincipalCannotSelectExportOwner() throws Exception {
        var ordinary = new UsernamePasswordAuthenticationToken(OWNER, null, List.of());
        for (String path : List.of("/api/exports/applications", "/api/exports/interviews")) {
            var response = mockMvc.perform(get(path).param("id", R301)
                            .header("x-user-id", OWNER)
                            .with(SecurityMockMvcRequestPostProcessors.authentication(ordinary)))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertThat(response.getContentAsString()).doesNotContain(OWNER, "示例,科技", "面经正文");
        }
    }

    @Test
    void bothExportAssertionsRejectAbsentForgedExpiredPathMismatchAndReplay() {
        for (String path : List.of("/api/exports/applications", "/api/exports/interviews")) {
            var verifier = new BridgeTokenVerifier(BridgeTokenFixtures.jobTraceProperties(),
                    Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC),
                    JsonMapper.builder().build());
            String valid = token(path, Map.of());
            assertFailure(verifier, null, path, BridgeAuthenticationFailure.ABSENT);
            assertFailure(verifier, BridgeTokenFixtures.token(
                    BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.PREVIOUS_SECRET,
                    OWNER, Map.of("pth", path)), path,
                    BridgeAuthenticationFailure.INVALID_SIGNATURE);
            assertFailure(verifier, token(path, Map.of(
                    "iat", Date.from(BridgeTokenFixtures.NOW.minusSeconds(40)),
                    "nbf", Date.from(BridgeTokenFixtures.NOW.minusSeconds(40)),
                    "exp", Date.from(BridgeTokenFixtures.NOW.minusSeconds(6)))), path,
                    BridgeAuthenticationFailure.INVALID_CLAIMS);
            String other = path.endsWith("applications") ? "/api/exports/interviews"
                    : "/api/exports/applications";
            assertFailure(verifier, valid, other, BridgeAuthenticationFailure.REQUEST_MISMATCH);
            var claimed = new HashSet<String>();
            ReplayGuard guard = new ReplayGuard() {
                @Override public boolean claim(String issuer, String tokenId, Instant retainUntil) {
                    return claimed.add(issuer + ":" + tokenId);
                }
                @Override public boolean isAvailable() { return true; }
            };
            var useCase = new ClaimAssertionUseCase(verifier, guard,
                    BridgeTokenFixtures.jobTraceProperties().authBridge());
            useCase.execute(valid, "GET", path, BridgeTokenFixtures.REQUEST_ID);
            assertThatThrownBy(() -> useCase.execute(valid, "GET", path,
                    BridgeTokenFixtures.REQUEST_ID))
                    .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                            failure -> assertThat(failure.failure())
                                    .isEqualTo(BridgeAuthenticationFailure.REPLAYED));
        }
    }

    private static String token(String path, Map<String, Object> overrides) {
        var values = new java.util.HashMap<String, Object>(overrides);
        values.put("pth", path);
        return BridgeTokenFixtures.token(BridgeTokenFixtures.CURRENT_KEY_ID,
                BridgeTokenFixtures.CURRENT_SECRET, OWNER, values);
    }

    private static void assertFailure(BridgeTokenVerifier verifier, String token,
            String path, BridgeAuthenticationFailure expected) {
        assertThatThrownBy(() -> verifier.verify(token, "GET", path,
                BridgeTokenFixtures.REQUEST_ID))
                .isInstanceOfSatisfying(BridgeAuthenticationException.class,
                        failure -> assertThat(failure.failure()).isEqualTo(expected));
    }
}
