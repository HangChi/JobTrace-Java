package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.BridgeIdentity;
import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class BridgePrincipalAdminTest {

    @Test
    void acceptsOnlyMatchingVerifiedAdministratorDetails() {
        var principal = new UsernamePasswordAuthenticationToken("admin-a", null, List.of());
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(principal)).isInstanceOf(RuntimeException.class);
        principal.setDetails(new BridgeIdentity("admin-a", BridgeIdentity.Role.USER, 2));
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(principal)).isInstanceOf(RuntimeException.class);
        principal.setDetails(new BridgeIdentity("admin-b", BridgeIdentity.Role.ADMIN, 2));
        assertThatThrownBy(() -> BridgePrincipalAdmin.require(principal)).isInstanceOf(RuntimeException.class);
        principal.setDetails(new BridgeIdentity("admin-a", BridgeIdentity.Role.ADMIN, 2));
        assertThat(BridgePrincipalAdmin.require(principal)).isEqualTo("admin-a");
    }
}
