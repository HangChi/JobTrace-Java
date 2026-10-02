package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.identityaccess.domain.BridgeIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class BridgePrincipalOwnerTest {

    @Test
    void acceptsOnlyMatchingBridgeDetails() {
        var principal = new UsernamePasswordAuthenticationToken("owner-a", null, List.of());
        assertThatThrownBy(() -> BridgePrincipalOwner.require(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> BridgePrincipalOwner.require(principal)).isInstanceOf(RuntimeException.class);
        principal.setDetails(new BridgeIdentity("owner-b", BridgeIdentity.Role.USER, 1));
        assertThatThrownBy(() -> BridgePrincipalOwner.require(principal)).isInstanceOf(RuntimeException.class);
        principal.setDetails(new BridgeIdentity("owner-a", BridgeIdentity.Role.USER, 1));
        assertThat(BridgePrincipalOwner.require(principal)).isEqualTo("owner-a");
    }
}
