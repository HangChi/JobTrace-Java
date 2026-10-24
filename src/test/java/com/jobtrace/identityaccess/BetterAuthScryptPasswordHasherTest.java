package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.infrastructure.BetterAuthScryptPasswordHasher;
import org.junit.jupiter.api.Test;

class BetterAuthScryptPasswordHasherTest {

    private static final String COMPATIBILITY_VECTOR =
            "00112233445566778899aabbccddeeff:"
            + "a9d2c62960732676e57d3e3c04f54f928381b887aba80d654cd370e4f738a908"
            + "c0b74e66905a77920938033a7448335a38ad4e0bd337ff3e586d30cd3e972ea6";

    private final BetterAuthScryptPasswordHasher hasher =
            new BetterAuthScryptPasswordHasher();

    @Test
    void verifiesFixedBetterAuthVectorIncludingNfkcNormalization() {
        assertThat(hasher.matches("Pässword-123", COMPATIBILITY_VECTOR)).isTrue();
        assertThat(hasher.matches("Pa\u0308ssword-123", COMPATIBILITY_VECTOR)).isTrue();
        assertThat(hasher.matches("Wrong-Password", COMPATIBILITY_VECTOR)).isFalse();
    }

    @Test
    void generatedHashesUseCompatibleShapeAndVerify() {
        String encoded = hasher.hash("Replacement");
        assertThat(encoded).matches("[0-9a-f]{32}:[0-9a-f]{128}");
        assertThat(hasher.matches("Replacement", encoded)).isTrue();
        assertThat(hasher.hash("Replacement")).isNotEqualTo(encoded);
    }

    @Test
    void rejectsMalformedRepresentations() {
        for (String encoded : new String[] {null, "", "salt:key", "A".repeat(161)}) {
            assertThatThrownBy(() -> hasher.matches("Password", encoded))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
