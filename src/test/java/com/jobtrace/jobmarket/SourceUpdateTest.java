package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.jobmarket.domain.SourceUpdate;
import org.junit.jupiter.api.Test;

class SourceUpdateTest {

    @Test
    void acceptsIndividualFieldsAndLimits() {
        assertThat(new SourceUpdate("active", null, null).status()).isEqualTo("active");
        assertThat(new SourceUpdate("revoked", null, null).status()).isEqualTo("revoked");
        assertThat(new SourceUpdate(null, 60, null).syncIntervalMinutes()).isEqualTo(60);
        assertThat(new SourceUpdate(null, 1440, null).syncIntervalMinutes()).isEqualTo(1440);
        assertThat(new SourceUpdate(null, null, "authorized").accessBasis())
                .isEqualTo("authorized");
    }

    @Test
    void rejectsEmptyInvalidStatusIntervalAndBasis() {
        assertThatThrownBy(() -> new SourceUpdate(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        for (String status : new String[] {"", "deleted", "ACTIVE"}) {
            assertThatThrownBy(() -> new SourceUpdate(status, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new SourceUpdate(null, 59, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceUpdate(null, 1441, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceUpdate(null, null, "private"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
