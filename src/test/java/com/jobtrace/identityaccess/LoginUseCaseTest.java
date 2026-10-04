package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.LoginAccount;
import com.jobtrace.identityaccess.application.LoginStore;
import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.domain.InvalidCredentialsException;
import com.jobtrace.identityaccess.domain.LoginValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginUseCaseTest {
    private final LoginStore store = mock(LoginStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final LoginAccount login = new LoginAccount(store, passwords);

    @BeforeEach
    void credentials() {
        when(store.findCredential(anyString(), eq(false))).thenReturn(
                new LoginStore.Credential("user-1", "hash", "user", false));
        when(passwords.matches("pass", "hash")).thenReturn(true);
        when(store.createSession("user-1", "browser")).thenReturn("token");
    }

    @Test
    void normalizesUsernameAndChoosesSafeDestination() {
        assertThat(login.execute(input("  Alice_1  ", "pass", "/jobs?tab=1")))
                .isEqualTo(new LoginAccount.Result("/jobs?tab=1", "token"));
        verify(store).findCredential("alice_1", false);
        assertThat(login.execute(input("Alice_1", "pass", "//evil.test"))
                .redirectTarget()).isEqualTo("/");
        assertThat(login.execute(input("Alice_1", "pass", "/\\evil.test"))
                .redirectTarget()).isEqualTo("/");
    }

    @Test
    void verifiedEmailAndRoleDefault() {
        when(store.findCredential("alice@example.test", true)).thenReturn(
                new LoginStore.Credential("user-1", "hash", "admin", false));
        assertThat(login.execute(input("Alice@Example.Test", "pass", null))
                .redirectTarget()).isEqualTo("/admin");
    }

    @Test
    void unknownWrongAndDisabledAllDenyWithoutSession() {
        when(store.findCredential("missing", false)).thenReturn(null);
        assertThatThrownBy(() -> login.execute(input("missing", "pass", null)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwords).hash("pass");
        when(passwords.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> login.execute(input("Alice_1", "wrong", null)))
                .isInstanceOf(InvalidCredentialsException.class);
        when(store.findCredential("disabled", false)).thenReturn(
                new LoginStore.Credential("user-1", "hash", "user", true));
        assertThatThrownBy(() -> login.execute(input("disabled", "pass", null)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(store, never()).createSession(anyString(), anyString());
    }

    @Test
    void validatesFieldsBeforeLookup() {
        for (var invalid : new LoginAccount.Input[] {
                input("a", "pass", null), input("invalid@email", "pass", null),
                input("Alice_1", "", null), input("Alice_1", "x".repeat(129), null)}) {
            assertThatThrownBy(() -> login.execute(invalid))
                    .isInstanceOf(LoginValidationException.class);
        }
        verify(store, never()).createSession(anyString(), anyString());
    }

    private static LoginAccount.Input input(String id, String password, String returnTo) {
        return new LoginAccount.Input(id, password, returnTo, "browser");
    }
}
