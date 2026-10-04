package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.AccountEmailUnbindingStore;
import com.jobtrace.identityaccess.application.AccountPasswordStore;
import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.application.UnbindAccountEmail;
import com.jobtrace.identityaccess.domain.AccountPasswordCredential;
import com.jobtrace.identityaccess.domain.AccountPasswordValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailUnbindingStateException;
import com.jobtrace.identityaccess.domain.InvalidCurrentPasswordException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountEmailUnbindingUseCaseTest {

    @Test
    void correctPasswordUsesVerifiedCredentialSnapshot() {
        var store = new RecordingStore();
        var useCase = new UnbindAccountEmail(credential(), hasher(true), store);

        var result = useCase.execute("owner", "session", "correct");

        assertThat(result.email()).isNull();
        assertThat(result.emailVerified()).isFalse();
        assertThat(store.attempts).isEqualTo(1);
        assertThat(store.unbound).isTrue();
        assertThat(store.expectedHash).isEqualTo("hash");
    }

    @Test
    void wrongAndInvalidPasswordsNeverUnbind() {
        var store = new RecordingStore();
        var useCase = new UnbindAccountEmail(credential(), hasher(false), store);

        assertThatThrownBy(() -> useCase.execute("owner", "session", "wrong"))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", ""))
                .isInstanceOf(AccountPasswordValidationException.class);
        assertThat(store.attempts).isEqualTo(1);
        assertThat(store.unbound).isFalse();
    }

    @Test
    void invalidOwnerSessionAndPasswordNeverConsumeAnAttempt() {
        var store = new RecordingStore();
        var useCase = new UnbindAccountEmail(credential(), hasher(true), store);

        for (String owner : new String[] {"", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> useCase.execute(owner, "session", "correct"))
                    .isInstanceOf(AccountEmailUnbindingStateException.class);
        }
        assertThatThrownBy(() -> useCase.execute(null, "session", "correct"))
                .isInstanceOf(AccountEmailUnbindingStateException.class);
        for (String session : new String[] {"", " ", "x".repeat(256)}) {
            assertThatThrownBy(() -> useCase.execute("owner", session, "correct"))
                    .isInstanceOf(AccountPasswordValidationException.class);
        }
        assertThatThrownBy(() -> useCase.execute("owner", null, "correct"))
                .isInstanceOf(AccountPasswordValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", null))
                .isInstanceOf(AccountPasswordValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "x".repeat(129)))
                .isInstanceOf(AccountPasswordValidationException.class);
        assertThat(store.attempts).isZero();
    }

    @Test
    void missingOrMalformedCredentialFailsWithoutEmailChange() {
        var store = new RecordingStore();
        AccountPasswordStore missing = new AccountPasswordStore() {
            @Override
            public Optional<AccountPasswordCredential> findCredential(
                    String ownerId, String currentSessionId) {
                return Optional.empty();
            }

            @Override
            public void replaceCredentialAndRevokeOtherSessions(
                    String ownerId, String currentSessionId, String accountId,
                    String expectedPasswordHash, String replacementPasswordHash) {
                throw new UnsupportedOperationException();
            }
        };
        assertThatThrownBy(() -> new UnbindAccountEmail(missing, hasher(true), store)
                .execute("owner", "session", "correct"))
                .isInstanceOf(AccountEmailUnbindingStateException.class);
        PasswordHasher malformed = new PasswordHasher() {
            @Override
            public boolean matches(String password, String encodedPassword) {
                throw new IllegalArgumentException("malformed credential");
            }

            @Override
            public String hash(String password) {
                throw new UnsupportedOperationException();
            }
        };
        assertThatThrownBy(() -> new UnbindAccountEmail(credential(), malformed, store)
                .execute("owner", "session", "correct"))
                .isInstanceOf(AccountEmailUnbindingStateException.class)
                .hasMessageNotContaining("malformed credential");
        assertThat(store.unbound).isFalse();
    }

    private static AccountPasswordStore credential() {
        return new AccountPasswordStore() {
            @Override
            public Optional<AccountPasswordCredential> findCredential(
                    String ownerId, String currentSessionId) {
                return Optional.of(new AccountPasswordCredential("account", "hash"));
            }

            @Override
            public void replaceCredentialAndRevokeOtherSessions(
                    String ownerId, String currentSessionId, String accountId,
                    String expectedPasswordHash, String replacementPasswordHash) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PasswordHasher hasher(boolean matches) {
        return new PasswordHasher() {
            @Override
            public boolean matches(String password, String encodedPassword) {
                return matches && password.equals("correct");
            }

            @Override
            public String hash(String password) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class RecordingStore implements AccountEmailUnbindingStore {
        private int attempts;
        private boolean unbound;
        private String expectedHash;

        @Override
        public void consumeChangeAttempt(String ownerId) {
            attempts++;
        }

        @Override
        public void unbind(String ownerId, String currentSessionId,
                String accountId, String expectedPasswordHash) {
            unbound = true;
            expectedHash = expectedPasswordHash;
        }
    }
}
