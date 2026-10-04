package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.AccountEmailBindingStore;
import com.jobtrace.identityaccess.application.AccountEmailChangeRateLimiter;
import com.jobtrace.identityaccess.application.AccountPasswordStore;
import com.jobtrace.identityaccess.application.BindAccountEmail;
import com.jobtrace.identityaccess.application.EmailVerificationCodeHasher;
import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.domain.AccountEmailBindingInvalidPasswordException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingStateException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.AccountPasswordCredential;
import com.jobtrace.identityaccess.domain.InvalidEmailBindingCodeException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class AccountEmailBindingUseCaseTest {

    private final AccountPasswordStore credentials = mock(AccountPasswordStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final AccountEmailChangeRateLimiter limiter = mock(AccountEmailChangeRateLimiter.class);
    private final EmailVerificationCodeHasher codes = mock(EmailVerificationCodeHasher.class);
    private final AccountEmailBindingStore store = mock(AccountEmailBindingStore.class);
    private final BindAccountEmail useCase = new BindAccountEmail(
            credentials, passwords, limiter, codes, store);

    @Test
    void bindsNormalizedAddressAfterPasswordAndCodeProof() {
        credentialAndPassword(true);
        when(codes.hash("owner", "new@example.com", "123456")).thenReturn("code-hash");
        when(store.bind("owner", "session", "account", "password-hash",
                "new@example.com", "code-hash")).thenReturn(true);

        var result = useCase.execute("owner", "session", "correct",
                "New@Example.COM", " 123456 ");

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.emailVerified()).isTrue();
        verify(limiter).consumeChangeAttempt("owner");
        verify(store).bind("owner", "session", "account", "password-hash",
                "new@example.com", "code-hash");
    }

    @Test
    void invalidFieldsFailBeforeRateLimitOrCodeUse() {
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "not-an-email", "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", "12345"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        verifyNoInteractions(limiter, store);
    }

    @Test
    void ownerAndInputBoundariesFailBeforePersistence() {
        for (String owner : new String[] {"", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> useCase.execute(owner, "session", "correct",
                    "new@example.com", "123456"))
                    .isInstanceOf(AccountEmailBindingStateException.class);
        }
        assertThatThrownBy(() -> useCase.execute(null, "session", "correct",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingStateException.class);
        for (String session : new String[] {"", " ", "x".repeat(256)}) {
            assertThatThrownBy(() -> useCase.execute("owner", session, "correct",
                    "new@example.com", "123456"))
                    .isInstanceOf(AccountEmailBindingValidationException.class);
        }
        assertThatThrownBy(() -> useCase.execute("owner", null, "correct",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", null,
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "x".repeat(129),
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                null, "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "x".repeat(255), "123456"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", null))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", "abcdef"))
                .isInstanceOf(AccountEmailBindingValidationException.class);
        verifyNoInteractions(limiter, credentials, passwords, codes, store);
    }

    @Test
    void missingCredentialAndWrongPasswordDoNotConsumeCode() {
        when(credentials.findCredential("owner", "session")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingStateException.class);
        credentialAndPassword(false);
        assertThatThrownBy(() -> useCase.execute("owner", "session", "wrong",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingInvalidPasswordException.class);
        verifyNoInteractions(codes, store);
    }

    @Test
    void passwordHashFailureDoesNotConsumeCode() {
        credentialAndPassword(true);
        when(passwords.matches("correct", "password-hash"))
                .thenThrow(new IllegalStateException("hash details"));
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailBindingStateException.class)
                .hasMessageNotContaining("hash details");
        verifyNoInteractions(codes, store);
    }

    @Test
    void invalidCodeAndUniqueConflictHaveDistinctSafeErrors() {
        credentialAndPassword(true);
        when(codes.hash(anyString(), anyString(), anyString())).thenReturn("code-hash");
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", "123456"))
                .isInstanceOf(InvalidEmailBindingCodeException.class);
        when(store.bind("owner", "session", "account", "password-hash",
                "new@example.com", "code-hash"))
                .thenThrow(new DuplicateKeyException("unique index"));
        assertThatThrownBy(() -> useCase.execute("owner", "session", "correct",
                "new@example.com", "123456"))
                .isInstanceOf(AccountEmailConflictException.class)
                .hasMessageNotContaining("index");
    }

    private void credentialAndPassword(boolean matches) {
        when(credentials.findCredential("owner", "session"))
                .thenReturn(Optional.of(new AccountPasswordCredential(
                        "account", "password-hash")));
        when(passwords.matches(anyString(), anyString())).thenReturn(matches);
    }
}
