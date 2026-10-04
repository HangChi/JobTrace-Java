package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.AccountPasswordStore;
import com.jobtrace.identityaccess.application.ChangeAccountPassword;
import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.domain.AccountPasswordChangeResult;
import com.jobtrace.identityaccess.domain.AccountPasswordCredential;
import com.jobtrace.identityaccess.domain.AccountPasswordStateException;
import com.jobtrace.identityaccess.domain.InvalidCurrentPasswordException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeAccountPasswordTest {

    @Test
    void verifiesCurrentPasswordAndPersistsReplacement() {
        var store = new RecordingStore();
        var hasher = new RecordingHasher(true);
        var useCase = new ChangeAccountPassword(store, hasher);

        assertThat(useCase.execute("owner", "current-session", "Current", "Replacement"))
                .isEqualTo(new AccountPasswordChangeResult());
        assertThat(hasher.matchedPassword).isEqualTo("Current");
        assertThat(hasher.hashedPassword).isEqualTo("Replacement");
        assertThat(store.replacement).isEqualTo("replacement-hash");
        assertThat(store.currentSession).isEqualTo("current-session");
    }

    @Test
    void wrongPasswordNeverWrites() {
        var store = new RecordingStore();
        var useCase = new ChangeAccountPassword(store, new RecordingHasher(false));

        assertThatThrownBy(() -> useCase.execute(
                "owner", "current-session", "Wrong", "Replacement"))
                .isInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessage("当前密码不正确，请重新输入。");
        assertThat(store.replacement).isNull();
    }

    @Test
    void validatesAllBoundariesBeforeReadingCredential() {
        var store = new RecordingStore();
        var useCase = new ChangeAccountPassword(store, new RecordingHasher(true));
        for (Runnable invalid : java.util.List.<Runnable>of(
                () -> useCase.execute(null, "session", "Current", "12345678"),
                () -> useCase.execute("owner", null, "Current", "12345678"),
                () -> useCase.execute("owner", "session", "", "12345678"),
                () -> useCase.execute("owner", "session", "Current", "1234567"),
                () -> useCase.execute("owner", "session", "Current", "1".repeat(17)))) {
            assertThatThrownBy(invalid::run).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(store.reads).isZero();
    }

    @Test
    void missingOrMalformedCredentialUsesSafeStateFailure() {
        AccountPasswordStore missing = new RecordingStore() {
            @Override
            public Optional<AccountPasswordCredential> findCredential(
                    String ownerId, String currentSessionId) {
                return Optional.empty();
            }
        };
        assertThatThrownBy(() -> new ChangeAccountPassword(
                missing, new RecordingHasher(true)).execute(
                        "owner", "session", "Current", "12345678"))
                .isInstanceOf(AccountPasswordStateException.class);

        PasswordHasher malformed = new PasswordHasher() {
            @Override
            public boolean matches(String password, String encodedPassword) {
                throw new IllegalArgumentException("bad hash");
            }

            @Override
            public String hash(String password) {
                return "unused";
            }
        };
        assertThatThrownBy(() -> new ChangeAccountPassword(
                new RecordingStore(), malformed).execute(
                        "owner", "session", "Current", "12345678"))
                .isInstanceOf(AccountPasswordStateException.class)
                .hasMessage("暂时无法修改密码，请稍后重试。");
    }

    private static class RecordingStore implements AccountPasswordStore {
        int reads;
        String replacement;
        String currentSession;

        @Override
        public Optional<AccountPasswordCredential> findCredential(
                String ownerId, String currentSessionId) {
            reads++;
            return Optional.of(new AccountPasswordCredential("credential", "current-hash"));
        }

        @Override
        public void replaceCredentialAndRevokeOtherSessions(
                String ownerId,
                String currentSessionId,
                String accountId,
                String expectedPasswordHash,
                String replacementPasswordHash) {
            this.currentSession = currentSessionId;
            this.replacement = replacementPasswordHash;
        }
    }

    private static final class RecordingHasher implements PasswordHasher {
        private final boolean matches;
        private String matchedPassword;
        private String hashedPassword;

        private RecordingHasher(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean matches(String password, String encodedPassword) {
            matchedPassword = password;
            return matches;
        }

        @Override
        public String hash(String password) {
            hashedPassword = password;
            return "replacement-hash";
        }
    }
}
