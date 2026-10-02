package com.jobtrace.identityaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BridgeIdentityTest {

    @Test
    void createsNormalizedUserAndAdminIdentities() {
        var user = new BridgeIdentity(" owner-1 ", BridgeIdentity.Role.USER, 0);

        assertThat(user.subject()).isEqualTo("owner-1");
        assertThat(BridgeIdentity.Role.fromClaim("user"))
                .isEqualTo(BridgeIdentity.Role.USER);
        assertThat(BridgeIdentity.Role.fromClaim("ADMIN"))
                .isEqualTo(BridgeIdentity.Role.ADMIN);
        assertThat(Arrays.asList(BridgeAuthenticationFailure.values()))
                .contains(BridgeAuthenticationFailure.REPLAYED);
    }

    @Test
    void rejectsInvalidSubjects() {
        assertThatThrownBy(() -> new BridgeIdentity(
                null, BridgeIdentity.Role.USER, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BridgeIdentity(
                " ", BridgeIdentity.Role.USER, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BridgeIdentity(
                "x".repeat(129), BridgeIdentity.Role.USER, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidAuthorizationContext() {
        assertThatThrownBy(() -> new BridgeIdentity("owner", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BridgeIdentity(
                "owner", BridgeIdentity.Role.USER, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BridgeIdentity.Role.fromClaim(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BridgeIdentity.Role.fromClaim("owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
