package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.ChangeAccountPassword;
import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.domain.AccountPasswordStateException;
import com.jobtrace.identityaccess.infrastructure.PostgresAccountPasswordStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountPasswordIntegrationTest extends AccountPasswordDatabaseTest {

    @Autowired
    private ChangeAccountPassword changePassword;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private PostgresAccountPasswordStore store;

    @Test
    void replacesOnlyOwnerCredentialAndKeepsOnlyCurrentSession() {
        String foreignHash = jdbc.queryForObject(
                "select password from accounts where user_id=?", String.class, OTHER_OWNER);

        changePassword.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD, "New-Password");

        assertThat(hasher.matches("New-Password", ownerHash())).isTrue();
        assertThat(hasher.matches(CURRENT_PASSWORD, ownerHash())).isFalse();
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION);
        assertThat(jdbc.queryForObject(
                "select password from accounts where user_id=?", String.class, OTHER_OWNER))
                .isEqualTo(foreignHash);
        assertThat(jdbc.queryForList(
                "select id from sessions where user_id=?", String.class, OTHER_OWNER))
                .containsExactly(FOREIGN_SESSION);
    }

    @Test
    void missingForeignAndExpiredCurrentSessionsCannotAuthorizeChange() {
        for (String session : new String[] {"missing", FOREIGN_SESSION}) {
            assertThatThrownBy(() -> changePassword.execute(
                    OWNER, session, CURRENT_PASSWORD, "New-Password"))
                    .isInstanceOf(AccountPasswordStateException.class);
            assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
            assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
        }

        jdbc.update("update sessions set expires_at=now()-interval '1 second' where id=?",
                CURRENT_SESSION);
        assertThatThrownBy(() -> changePassword.execute(
                OWNER, CURRENT_SESSION, CURRENT_PASSWORD, "New-Password"))
                .isInstanceOf(AccountPasswordStateException.class);
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
    }

    @Test
    void conditionalReplacementRejectsCredentialRaceBeforeSessionDeletion() {
        assertThatThrownBy(() -> store.replaceCredentialAndRevokeOtherSessions(
                OWNER, CURRENT_SESSION, "credential-owner", "stale-hash", "replacement"))
                .isInstanceOf(AccountPasswordStateException.class);
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

}
