package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
import com.jobtrace.identityaccess.infrastructure.BetterAuthEmailCodeHasher;
import org.junit.jupiter.api.Test;

class RegistrationCodeHasherTest {

    @Test
    void matchesFixedLegacyNodeVectorAndSeparatesPurpose() {
        var hasher = new BetterAuthEmailCodeHasher("0123456789abcdef0123456789abcdef");
        assertThat(hasher.hash("new@example.com", "123456"))
                .isEqualTo("2bedad8d9b3fbe50fb26acc81eb31b8cacddc4a46f8e06f8f83ddd0eb6f375cc");
        assertThat(hasher.hash("owner", "new@example.com", "123456"))
                .isNotEqualTo(hasher.hash("new@example.com", "123456"));
    }

    @Test
    void absentSecretFailsClosed() {
        assertThatThrownBy(() -> new BetterAuthEmailCodeHasher("")
                .hash("new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingUnavailableException.class);
    }
}
