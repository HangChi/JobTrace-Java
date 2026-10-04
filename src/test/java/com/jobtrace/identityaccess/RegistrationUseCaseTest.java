package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.application.RegisterAccount;
import com.jobtrace.identityaccess.application.RegistrationEmailCodeHasher;
import com.jobtrace.identityaccess.application.RegistrationStore;
import com.jobtrace.identityaccess.domain.InvalidRegistrationCodeException;
import com.jobtrace.identityaccess.domain.RegistrationValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RegistrationUseCaseTest {

    private final RegistrationStore store = Mockito.mock(RegistrationStore.class);
    private final RegistrationEmailCodeHasher codeHasher =
            Mockito.mock(RegistrationEmailCodeHasher.class);
    private final PasswordHasher passwordHasher = Mockito.mock(PasswordHasher.class);
    private RegisterAccount registration;

    @BeforeEach
    void setup() {
        registration = new RegisterAccount(store, codeHasher, passwordHasher);
    }

    @Test
    void normalizesFieldsAndCreatesCredentialOnlyAfterVerification() {
        when(codeHasher.hash("new@example.test", "123456")).thenReturn("code-hash");
        when(store.verifyCode("new@example.test", "code-hash")).thenReturn(true);
        when(passwordHasher.hash("Password123!")).thenReturn("password-hash");

        registration.execute(new RegisterAccount.Input("  New_User  ", "NEW@example.test",
                " 123456 ", "Password123!", "  Display Name  "));

        verify(store).createAccount("new_user", "new@example.test", "password-hash",
                "Display Name");
    }

    @Test
    void rejectsInvalidCodeWithoutCreatingAccount() {
        when(codeHasher.hash("new@example.test", "123456")).thenReturn("code-hash");
        assertThatThrownBy(() -> registration.execute(new RegisterAccount.Input(
                "new_user", "new@example.test", "123456", "Password123!", null)))
                .isInstanceOf(InvalidRegistrationCodeException.class);
        verify(store, never()).createAccount(any(), any(), any(), any());
    }

    @Test
    void validatesUsernamePasswordAndCodeBeforeStorage() {
        assertInvalid("bad name", "new@example.test", "123456", "Password123!", "username");
        assertInvalid("new_user", "new@example.test", "12345", "Password123!",
                "verificationCode");
        assertInvalid("new_user", "new@example.test", "123456", "short", "password");
        assertInvalid("new_user", "not-an-email", "123456", "Password123!", "email");
        assertInvalid("new_user", "new@example.test", "123456", "Password123!",
                "x".repeat(101), "displayName");
        assertInvalid("new_user", "new@example.test", "123456", "Password123!",
                "   ", "displayName");
        assertInvalid(null, "new@example.test", "123456", "Password123!", "username");
        assertInvalid("new_user", null, "123456", "Password123!", "email");
        assertInvalid("new_user", "new@example.test", null, "Password123!",
                "verificationCode");
        assertInvalid("new_user", "new@example.test", "123456", null, "password");
        assertInvalid("new_user", "new@example.test", "123456", "x".repeat(17),
                "password");
        verify(store, never()).verifyCode(any(), any());
    }

    @Test
    void nullInputIsRejectedAndEmptyDisplayNameFallsBackToUsername() {
        assertThatThrownBy(() -> registration.execute(null))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("username"));
        when(codeHasher.hash("new@example.test", "123456")).thenReturn("code-hash");
        when(store.verifyCode("new@example.test", "code-hash")).thenReturn(true);
        when(passwordHasher.hash("Password123!")).thenReturn("password-hash");
        registration.execute(new RegisterAccount.Input("New_User", "new@example.test",
                "123456", "Password123!", ""));
        verify(store).createAccount("new_user", "new@example.test", "password-hash",
                "new_user");
    }

    private void assertInvalid(String username, String email, String code, String password,
            String field) {
        assertInvalid(username, email, code, password, null, field);
    }

    private void assertInvalid(String username, String email, String code, String password,
            String displayName, String field) {
        assertThatThrownBy(() -> registration.execute(new RegisterAccount.Input(
                username, email, code, password, displayName)))
                .isInstanceOf(RegistrationValidationException.class)
                .satisfies(error -> assertThat(((RegistrationValidationException) error).field())
                        .isEqualTo(field));
    }
}
