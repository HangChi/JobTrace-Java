package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.domain.BrowserIdentity;
import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.shared.web.Problem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class BrowserPrincipalAuthorizationTest {
    @Test
    void browserAdminUsesCurrentVerifiedRole() {
        assertThat(BridgePrincipalAdmin.require(browser("owner", "owner",
                BrowserIdentity.Role.ADMIN))).isEqualTo("owner");
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(browser("owner", "owner",
                BrowserIdentity.Role.USER)))
                .isInstanceOfSatisfying(Problem.class,
                        problem -> assertThat(problem.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(browser("other", "owner",
                BrowserIdentity.Role.ADMIN)))
                .isInstanceOfSatisfying(Problem.class,
                        problem -> assertThat(problem.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void bridgeAdminAndUnauthenticatedCallersRetainTheirOriginalBoundary() {
        var bridgeAdmin = new UsernamePasswordAuthenticationToken("owner", null, List.of());
        bridgeAdmin.setDetails(new BridgeIdentity("owner", BridgeIdentity.Role.ADMIN, 3));
        assertThat(BridgePrincipalAdmin.require(bridgeAdmin)).isEqualTo("owner");
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(null))
                .isInstanceOfSatisfying(Problem.class,
                        problem -> assertThat(problem.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        var bridgeUser = new UsernamePasswordAuthenticationToken("owner", null, List.of());
        bridgeUser.setDetails(new BridgeIdentity("owner", BridgeIdentity.Role.USER, 3));
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(bridgeUser))
                .isInstanceOfSatisfying(Problem.class,
                        problem -> assertThat(problem.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void browserSessionIdOverridesUntrustedHeaderButBridgeKeepsVerifiedMetadata() {
        var browser = browser("owner", "owner", BrowserIdentity.Role.USER);
        assertThat(BridgePrincipalOwner.require(browser)).isEqualTo("owner");
        assertThat(BridgePrincipalOwner.currentSessionId(browser, "forged"))
                .isEqualTo("browser-session");
        var bridge = new UsernamePasswordAuthenticationToken("owner", null, List.of());
        bridge.setDetails(new BridgeIdentity("owner", BridgeIdentity.Role.USER, 3));
        assertThat(BridgePrincipalOwner.currentSessionId(bridge, "verified-session"))
                .isEqualTo("verified-session");
        assertThatThrownBy(() -> BridgePrincipalOwner.currentSessionId(null, "forged"))
                .isInstanceOf(Problem.class);
    }

    private static UsernamePasswordAuthenticationToken browser(String principal, String subject,
            BrowserIdentity.Role role) {
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setDetails(new BrowserIdentity(subject, "browser-session", role));
        return authentication;
    }
}
