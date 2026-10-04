package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.UnbindAccountEmail;
import com.jobtrace.identityaccess.domain.AccountEmailRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailUnbindingStateException;
import com.jobtrace.identityaccess.domain.InvalidCurrentPasswordException;
import com.jobtrace.identityaccess.infrastructure.PostgresAccountEmailUnbindingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

@SpringBootTest
class AccountEmailUnbindingDatabaseTest extends AccountEmailUnbindingFixture {

    @Autowired
    private UnbindAccountEmail unbindEmail;

    @Autowired
    private PostgresAccountEmailUnbindingStore store;

    @Test
    void clearsOnlyOwnerEmailAndPendingCodes() {
        var result = unbindEmail.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD);

        assertThat(result.email()).isNull();
        assertThat(result.emailVerified()).isFalse();
        assertThat(recoveryEmail(OWNER)).isNull();
        assertThat(emailVerified(OWNER)).isFalse();
        assertThat(consumedCodes(OWNER)).containsExactly(true, true);
        assertThat(recoveryEmail(OTHER_OWNER)).isEqualTo(FOREIGN_EMAIL);
        assertThat(consumedCodes(OTHER_OWNER)).containsExactly(false);
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void alreadyUnboundOwnerKeepsSameResponseAndConsumesPendingCodes() {
        jdbc.update("""
                update users set recovery_email=null,recovery_email_verified_at=null,
                  email_verified=false where id=?
                """, OWNER);
        var result = unbindEmail.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD);
        assertThat(result.email()).isNull();
        assertThat(result.emailVerified()).isFalse();
        assertThat(consumedCodes(OWNER)).containsExactly(true, true);
        assertThat(consumedCodes(OTHER_OWNER)).containsExactly(false);
    }

    @Test
    void wrongPasswordAndForeignSessionDoNotMutate() {
        assertThatThrownBy(() -> unbindEmail.execute(OWNER, CURRENT_SESSION, "wrong"))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        assertThatThrownBy(() -> unbindEmail.execute(OWNER, FOREIGN_SESSION, CURRENT_PASSWORD))
                .isInstanceOf(AccountEmailUnbindingStateException.class);
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(consumedCodes(OWNER)).containsExactly(false, false);
    }

    @Test
    void sixthAttemptIsRejectedWithoutMutation() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> unbindEmail.execute(OWNER, CURRENT_SESSION, "wrong"))
                    .isInstanceOf(InvalidCurrentPasswordException.class);
        }
        assertThatThrownBy(() -> unbindEmail.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD))
                .isInstanceOf(AccountEmailRateLimitedException.class);
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(consumedCodes(OWNER)).containsExactly(false, false);
    }

    @Test
    void codeUpdateFailureRollsBackOwnerEmailUpdate() {
        jdbc.execute("drop table email_verification_codes");
        assertThatThrownBy(() -> unbindEmail.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD))
                .isInstanceOf(DataAccessException.class);
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(emailVerified(OWNER)).isTrue();
    }

    @Test
    void changedCredentialOrExpiredSessionFailsBeforeMutation() {
        assertThatThrownBy(() -> store.unbind(
                OWNER, CURRENT_SESSION, "credential-owner", "stale-hash"))
                .isInstanceOf(AccountEmailUnbindingStateException.class);
        jdbc.update("update sessions set expires_at=now()-interval '1 second' where id=?",
                CURRENT_SESSION);
        assertThatThrownBy(() -> store.unbind(
                OWNER, CURRENT_SESSION, "credential-owner", CURRENT_HASH))
                .isInstanceOf(AccountEmailUnbindingStateException.class);
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(consumedCodes(OWNER)).containsExactly(false, false);
    }
}
