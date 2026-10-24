package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthEmailCodeHasher;
import org.junit.jupiter.api.Test;

class AccountEmailBindingHasherTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void matchesFixedLegacyNodeHmacVector() {
        var hasher = new BetterAuthEmailCodeHasher(SECRET);
        assertThat(hasher.hash("owner-123", "new@example.com", "123456"))
                .isEqualTo("c13b99246dc9f921fe1c5eda7b13adff00cc1765b33cec45710fe83f18f7c7e2");
        assertThat(hasher.hash("owner-124", "new@example.com", "123456"))
                .isNotEqualTo(hasher.hash("owner-123", "new@example.com", "123456"));
    }

    @Test
    void missingSecretFailsClosed() {
        assertThatThrownBy(() -> new BetterAuthEmailCodeHasher("")
                .hash("owner", "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingUnavailableException.class);
        assertThatThrownBy(() -> new BetterAuthEmailCodeHasher(null)
                .hash("owner", "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingUnavailableException.class);
    }
}
