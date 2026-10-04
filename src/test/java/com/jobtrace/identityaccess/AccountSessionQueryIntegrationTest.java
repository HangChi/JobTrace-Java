package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.AccountSessionReadQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountSessionQueryIntegrationTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private AccountSessionReadQuery query;

    @Test
    void returnsOnlyActiveOwnedSessionsWithStableOrderAndCurrentMarker() {
        var sessions = query.listActive(OWNER, CURRENT);
        assertThat(sessions).extracting(session -> session.id())
                .containsExactly(CURRENT, "session-older", "session-null-agent");
        assertThat(sessions).extracting(session -> session.current())
                .containsExactly(true, false, false);
        assertThat(sessions.get(2).userAgent()).isNull();
        assertThat(sessions.getFirst().createdAt()).isEqualTo("2026-10-02T09:30:00.000Z");
        assertThat(sessions).extracting(session -> session.id())
                .doesNotContain("session-expired", "session-foreign");
    }

    @Test
    void unmatchedOrForeignCurrentReferenceMarksNothingAndDoesNotMutateRows() {
        int before = jdbc.queryForObject("select count(*) from sessions", Integer.class);
        assertThat(query.listActive(OWNER, "session-foreign"))
                .allMatch(session -> !session.current());
        assertThat(query.listActive("missing-owner", CURRENT)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(before);
    }
}
