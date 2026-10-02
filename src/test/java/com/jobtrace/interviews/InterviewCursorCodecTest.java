package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.interviews.domain.InterviewCursorCodec;
import com.jobtrace.interviews.domain.InterviewCursorCodec.Cursor;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InterviewCursorCodecTest {

    private final InterviewCursorCodec codec = new InterviewCursorCodec(new JsonMapper());

    @Test
    void roundTripsDateAndUuidWithoutOwnerAuthority() {
        var cursor = new Cursor(LocalDate.of(2026, 9, 20), UUID.fromString(
                "00000000-0000-0000-0000-000000000102"));
        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
        assertThat(new String(Base64.getUrlDecoder().decode(codec.encode(cursor))))
                .contains("\"value\":\"2026-09-20\"")
                .doesNotContain("owner");
    }

    @Test
    void rejectsMalformedOrIncompleteNavigationData() {
        for (String invalid : new String[] {"?", encoded("{}"),
                encoded("{\"value\":\"bad\",\"id\":\"00000000-0000-0000-0000-000000000102\"}"),
                encoded("{\"value\":\"2026-09-20\",\"id\":\"bad\"}")}) {
            assertThatThrownBy(() -> codec.decode(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static String encoded(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
