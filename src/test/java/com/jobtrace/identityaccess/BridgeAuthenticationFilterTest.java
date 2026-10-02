package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jobtrace.identityaccess.application.ClaimAssertionUseCase;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.domain.ReplayGuard;
import com.jobtrace.identityaccess.web.BridgeAuthenticationFilter;
import com.jobtrace.identityaccess.web.BridgeAuthenticationMetrics;
import com.jobtrace.identityaccess.web.BridgeTokenVerifier;
import com.jobtrace.shared.security.BridgeAuthenticationEntryPoint;
import com.jobtrace.shared.web.RequestIdFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class BridgeAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesPrincipalOnlyFromAValidAssertion() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        MockHttpServletRequest request = request();
        request.addHeader(
                "Authorization",
                "JobTraceBridge " + BridgeTokenFixtures.validToken("owner-a"));
        request.addHeader("x-user-id", "attacker");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication[] observed = new Authentication[1];

        filter.doFilter(request, response, (incoming, outgoing) ->
                observed[0] = SecurityContextHolder.getContext().getAuthentication());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(observed[0].getName()).isEqualTo("owner-a");
        assertThat(observed[0].getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
        assertThat(observed[0].getDetails())
                .isEqualTo(new BridgeIdentity("owner-a", BridgeIdentity.Role.USER, 3));
    }

    @Test
    void rejectsAbsentAssertionEvenWhenPublicIdentityHeaderIsPresent() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        MockHttpServletRequest request = request();
        request.addHeader("x-user-id", "owner-a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"unauthorized\"")
                .contains(BridgeTokenFixtures.REQUEST_ID)
                .doesNotContain("owner-a");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void consumesAnAssertionExactlyOnce() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        String authorization =
                "JobTraceBridge " + BridgeTokenFixtures.validToken("owner-a");

        MockHttpServletResponse first = invoke(filter, authorization);
        SecurityContextHolder.clearContext();
        MockHttpServletResponse second = invoke(filter, authorization);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(401);
    }

    @Test
    void responseAndSafeLogNeverDiscloseAssertionOrSubject() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(BridgeAuthenticationEntryPoint.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String malformed = "secret-token-value";
        try {
            MockHttpServletResponse response = invoke(
                    filter(new InMemoryReplayGuard()),
                    "JobTraceBridge " + malformed);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString())
                    .doesNotContain(malformed)
                    .doesNotContain("owner-a");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(malformed)
                            .doesNotContain("owner-a"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void bypassesEveryRouteOutsideTheSingleProtectedSurface() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health/live");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void protectsOnlyExactPrivateInterviewAndApplicationPaths() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        String id = "00000000-0000-0000-0000-000000000101";
        for (String path : new String[] {
                "/api/interviews", "/api/interviews/" + id,
                "/api/applications/" + id + "/detail"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as(path).isEqualTo(401);
        }
        for (String path : new String[] {
                "/api/interviews/public", "/api/interviews/public/" + id,
                "/api/interviews/" + id + "/comments",
                "/api/applications/" + id + "/detail/extra"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
    }

    @Test
    void protectsOnlyReminderReadPathsAndNotMutationOrDeliveryPaths() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        for (String path : new String[] {"/api/reminders", "/api/reminder-settings"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as(path).isEqualTo(401);
        }
        for (String path : new String[] {
                "/api/reminders/internal", "/api/internal/reminders/deliver",
                "/api/reminders/00000000-0000-0000-0000-000000000101"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
        for (String path : new String[] {"/api/reminders", "/api/reminder-settings"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
    }

    @Test
    void reminderAssertionIsBoundToExactMethodAndPath() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        String token = BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.CURRENT_SECRET,
                "owner-a", java.util.Map.of("pth", "/api/reminders"));
        MockHttpServletRequest wrongPath = new MockHttpServletRequest(
                "GET", "/api/reminder-settings");
        wrongPath.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        wrongPath.addHeader("Authorization", "JobTraceBridge " + token);

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(wrongPath, rejected, new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(401);

        MockHttpServletRequest exactPath = new MockHttpServletRequest("GET", "/api/reminders");
        exactPath.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        exactPath.addHeader("Authorization", "JobTraceBridge " + token);
        MockFilterChain acceptedChain = new MockFilterChain();
        filter.doFilter(exactPath, new MockHttpServletResponse(), acceptedChain);
        assertThat(acceptedChain.getRequest()).isSameAs(exactPath);

        String wrongMethodToken = BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.CURRENT_SECRET,
                "owner-a", java.util.Map.of("pth", "/api/reminders", "mth", "POST"));
        MockHttpServletRequest wrongMethod = new MockHttpServletRequest("GET", "/api/reminders");
        wrongMethod.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        wrongMethod.addHeader("Authorization", "JobTraceBridge " + wrongMethodToken);
        MockHttpServletResponse methodRejected = new MockHttpServletResponse();
        filter.doFilter(wrongMethod, methodRejected, new MockFilterChain());
        assertThat(methodRejected.getStatus()).isEqualTo(401);
    }

    @Test
    void protectsOnlyExactExportGetsAndNotImportOrMutations() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        for (String path : new String[] {
                "/api/exports/applications", "/api/exports/interviews"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as(path).isEqualTo(401);
            for (String method : new String[] {"POST", "PATCH", "DELETE"}) {
                MockHttpServletRequest mutation = new MockHttpServletRequest(method, path);
                MockFilterChain chain = new MockFilterChain();
                filter.doFilter(mutation, new MockHttpServletResponse(), chain);
                assertThat(chain.getRequest()).as(method + " " + path).isSameAs(mutation);
            }
        }
        for (String path : new String[] {
                "/api/imports/preview", "/api/exports/applications/extra",
                "/api/exports/interviews/extra"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
    }

    @Test
    void exportAssertionCannotBeReplayedAcrossPathsOrMethods() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        String token = BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.CURRENT_SECRET,
                "owner-a", java.util.Map.of("pth", "/api/exports/applications"));
        MockHttpServletRequest wrongPath = new MockHttpServletRequest(
                "GET", "/api/exports/interviews");
        wrongPath.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        wrongPath.addHeader("Authorization", "JobTraceBridge " + token);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(wrongPath, rejected, new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(401);

        MockHttpServletRequest exactPath = new MockHttpServletRequest(
                "GET", "/api/exports/applications");
        exactPath.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        exactPath.addHeader("Authorization", "JobTraceBridge " + token);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(exactPath, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(exactPath);

        String wrongMethodToken = BridgeTokenFixtures.token(
                BridgeTokenFixtures.CURRENT_KEY_ID, BridgeTokenFixtures.CURRENT_SECRET,
                "owner-a", java.util.Map.of("pth", "/api/exports/applications", "mth", "POST"));
        MockHttpServletRequest wrongMethod = new MockHttpServletRequest(
                "GET", "/api/exports/applications");
        wrongMethod.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        wrongMethod.addHeader("Authorization", "JobTraceBridge " + wrongMethodToken);
        MockHttpServletResponse methodRejected = new MockHttpServletResponse();
        filter.doFilter(wrongMethod, methodRejected, new MockFilterChain());
        assertThat(methodRejected.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWrongEmptyAndMultiPartAuthorizationSchemes() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());

        assertThat(invoke(filter, "Bearer value").getStatus()).isEqualTo(401);
        assertThat(invoke(filter, "JobTraceBridge ").getStatus()).isEqualTo(401);
        assertThat(invoke(filter, "JobTraceBridge first second").getStatus())
                .isEqualTo(401);
    }

    @Test
    void rejectsAValidAssertionWhenNormalizedRequestIdIsMissing() throws Exception {
        BridgeAuthenticationFilter filter = filter(new InMemoryReplayGuard());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                BridgeAuthenticationFilter.PROTECTED_PATH);
        request.addHeader(
                "Authorization",
                "JobTraceBridge " + BridgeTokenFixtures.validToken("owner-a"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static MockHttpServletResponse invoke(
            BridgeAuthenticationFilter filter,
            String authorization) throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                BridgeAuthenticationFilter.PROTECTED_PATH);
        request.setAttribute(
                RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                BridgeTokenFixtures.REQUEST_ID);
        return request;
    }

    private static BridgeAuthenticationFilter filter(ReplayGuard replayGuard) {
        var properties = BridgeTokenVerifierTest.properties();
        var verifier = new BridgeTokenVerifier(
                BridgeTokenVerifierTest.jobTraceProperties(),
                Clock.fixed(BridgeTokenFixtures.NOW, ZoneOffset.UTC),
                JsonMapper.builder().build());
        var useCase = new ClaimAssertionUseCase(verifier, replayGuard, properties);
        var registry = new SimpleMeterRegistry();
        return new BridgeAuthenticationFilter(
                useCase,
                new BridgeAuthenticationEntryPoint(),
                new BridgeAuthenticationMetrics(registry));
    }

    private static final class InMemoryReplayGuard implements ReplayGuard {

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
