package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.AccountSessionReadQuery;
import com.jobtrace.identityaccess.application.ListAccountSessions;
import com.jobtrace.identityaccess.domain.AccountSession;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ListAccountSessionsTest {

    @Test
    void validatesInputAndReturnsAnImmutableCopy() {
        AtomicReference<String> owner = new AtomicReference<>();
        AtomicReference<String> current = new AtomicReference<>();
        AccountSessionReadQuery query = (ownerId, currentSessionId) -> {
            owner.set(ownerId);
            current.set(currentSessionId);
            return List.of(new AccountSession("one", "created", "expires", null, true));
        };
        var result = new ListAccountSessions(query).execute("owner", "one");
        assertThat(owner).hasValue("owner");
        assertThat(current).hasValue("one");
        assertThat(result).hasSize(1);
        assertThatThrownBy(() -> result.add(result.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingAndOverlongOwnerOrSessionIdentifiers() {
        var useCase = new ListAccountSessions((owner, current) -> List.of());
        for (String owner : new String[] {null, "", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> useCase.execute(owner, "session"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String current : new String[] {null, "", " ", "x".repeat(256)}) {
            assertThatThrownBy(() -> useCase.execute("owner", current))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void accountSessionRejectsInvalidRequiredValues() {
        assertThatThrownBy(() -> new AccountSession(null, "created", "expires", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountSession("x".repeat(256),
                "created", "expires", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountSession("id", null, "expires", null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccountSession("id", "created", null, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
