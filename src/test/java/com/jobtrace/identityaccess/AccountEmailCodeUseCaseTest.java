package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.application.AccountEmailCodeGenerator;
import com.jobtrace.identityaccess.application.AccountEmailCodeStore;
import com.jobtrace.identityaccess.application.EmailVerificationCodeHasher;
import com.jobtrace.identityaccess.application.RequestAccountEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import org.junit.jupiter.api.Test;

class AccountEmailCodeUseCaseTest {

    private final AccountEmailCodeStore store = mock(AccountEmailCodeStore.class);
    private final AccountEmailCodeGenerator generator = mock(AccountEmailCodeGenerator.class);
    private final EmailVerificationCodeHasher hasher = mock(EmailVerificationCodeHasher.class);
    private final AccountEmailCodeDelivery delivery = mock(AccountEmailCodeDelivery.class);
    private final RequestAccountEmailCode useCase = new RequestAccountEmailCode(
            store, generator, hasher, delivery);

    @Test
    void issuesNormalizedCodeAndDeliversOnlyAfterPersistence() {
        when(generator.generate()).thenReturn("012345");
        when(hasher.hash("owner", "new@example.com", "012345")).thenReturn("stored-hash");

        useCase.execute("owner", "session", "New@Example.COM");

        verify(delivery).ensureAvailable();
        verify(store).consumeSourceAttempt();
        verify(store).issue("owner", "session", "new@example.com", "stored-hash");
        verify(delivery).deliver("new@example.com", "012345");
    }

    @Test
    void rejectsOwnerSessionAndAddressBeforeSideEffects() {
        for (String owner : new String[] {"", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> useCase.execute(owner, "session", "new@example.com"))
                    .isInstanceOf(AccountEmailCodeStateException.class);
        }
        assertThatThrownBy(() -> useCase.execute(null, "session", "new@example.com"))
                .isInstanceOf(AccountEmailCodeStateException.class);
        for (String session : new String[] {"", " ", "x".repeat(256)}) {
            assertThatThrownBy(() -> useCase.execute("owner", session, "new@example.com"))
                    .isInstanceOf(AccountEmailCodeValidationException.class);
        }
        assertThatThrownBy(() -> useCase.execute("owner", null, "new@example.com"))
                .isInstanceOf(AccountEmailCodeValidationException.class);
        for (String address : new String[] {"", "broken", "x".repeat(255)}) {
            assertThatThrownBy(() -> useCase.execute("owner", "session", address))
                    .isInstanceOf(AccountEmailCodeValidationException.class);
        }
        assertThatThrownBy(() -> useCase.execute("owner", "session", null))
                .isInstanceOf(AccountEmailCodeValidationException.class);
        verifyNoInteractions(store, generator, hasher, delivery);
    }

    @Test
    void unavailableDeliveryFailsBeforeRateLimitAndCodeInsertion() {
        doThrow(new AccountEmailCodeDeliveryException(true, false))
                .when(delivery).ensureAvailable();
        assertThatThrownBy(() -> useCase.execute("owner", "session", "new@example.com"))
                .isInstanceOf(AccountEmailCodeDeliveryException.class);
        verifyNoInteractions(store, generator, hasher);
    }
}
