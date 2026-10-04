package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.RevokeAccountSession;
import com.jobtrace.identityaccess.domain.AccountSessionNotFoundException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RevokeAccountSessionTest {

    @Test
    void delegatesVerifiedOwnerAndOpaqueSessionAndReturnsConfirmation() {
        AtomicReference<String> owner = new AtomicReference<>();
        AtomicReference<String> session = new AtomicReference<>();
        var useCase = new RevokeAccountSession((ownerId, sessionId) -> {
            owner.set(ownerId);
            session.set(sessionId);
            return true;
        });

        assertThat(useCase.execute("owner-a", "Case-Sensitive").revoked()).isTrue();
        assertThat(owner).hasValue("owner-a");
        assertThat(session).hasValue("Case-Sensitive");
    }

    @Test
    void missingOrForeignSessionUsesTheSameSafeException() {
        var useCase = new RevokeAccountSession((owner, session) -> false);
        assertThatThrownBy(() -> useCase.execute("owner-a", "missing"))
                .isInstanceOf(AccountSessionNotFoundException.class)
                .hasMessage("登录会话已不存在。");
    }

    @Test
    void rejectsMissingBlankAndOverlongIdentifiersBeforeMutation() {
        int[] calls = {0};
        var useCase = new RevokeAccountSession((owner, session) -> {
            calls[0]++;
            return true;
        });
        for (String owner : new String[] {null, "", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> useCase.execute(owner, "session"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String session : new String[] {null, "", " ", "x".repeat(256)}) {
            assertThatThrownBy(() -> useCase.execute("owner", session))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(calls[0]).isZero();
    }
}
