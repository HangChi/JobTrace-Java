package com.jobtrace.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.applications.domain.ApplicationCursorCodec;
import com.jobtrace.applications.domain.ApplicationCursorCodec.Cursor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApplicationCursorCodecTest {

    private final ApplicationCursorCodec codec = new ApplicationCursorCodec(JsonMapper.builder().build());

    @Test
    void roundTripsTheLegacyBase64UrlJsonShape() {
        Cursor cursor = new Cursor(
                "2026-09-10",
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                0);

        String encoded = codec.encode(cursor);

        assertThat(encoded).isEqualTo(
                "eyJ2YWx1ZSI6IjIwMjYtMDktMTAiLCJpZCI6IjEwMDAwMDAwLTAwMDAtNDAwMC04MDAwLTAwMDAwMDAwMDAwMSIs"
                        + "InN0YXR1c1JhbmsiOjB9");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedOrIncompleteCursors() {
        assertThatThrownBy(() -> codec.encode(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> codec.decode("not-base64-json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("eyJ2YWx1ZSI6IiJ9"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("eyJ2YWx1ZSI6IjIwMjYtMDktMTAiLCJpZCI6Im5vdC1hLXV1aWQifQ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cursor(
                        "value",
                        UUID.fromString("10000000-0000-4000-8000-000000000001"),
                        -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cursor(
                        "value",
                        UUID.fromString("10000000-0000-4000-8000-000000000001"),
                        3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cursor("", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void omitsTheOptionalStatusRank() {
        Cursor cursor = new Cursor(
                "Acme",
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                null);

        String encoded = codec.encode(cursor);

        assertThat(encoded).isEqualTo(
                "eyJ2YWx1ZSI6IkFjbWUiLCJpZCI6IjEwMDAwMDAwLTAwMDAtNDAwMC04MDAwLTAwMDAwMDAwMDAwMSJ9");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }
}
