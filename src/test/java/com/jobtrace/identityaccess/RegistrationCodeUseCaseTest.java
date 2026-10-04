package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.application.AccountEmailCodeGenerator;
import com.jobtrace.identityaccess.application.RegistrationEmailCodeHasher;
import com.jobtrace.identityaccess.application.RegistrationEmailCodeStore;
import com.jobtrace.identityaccess.application.RequestRegistrationEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeValidationException;
import org.junit.jupiter.api.Test;

class RegistrationCodeUseCaseTest {

    private final RegistrationEmailCodeStore store = mock(RegistrationEmailCodeStore.class);
    private final AccountEmailCodeGenerator generator = mock(AccountEmailCodeGenerator.class);
    private final RegistrationEmailCodeHasher hasher = mock(RegistrationEmailCodeHasher.class);
    private final AccountEmailCodeDelivery delivery = mock(AccountEmailCodeDelivery.class);
    private final RequestRegistrationEmailCode useCase = new RequestRegistrationEmailCode(
            store, generator, hasher, delivery);

    @Test
    void normalizesAndDeliversAfterRegistrationCodeInsertion() {
        when(generator.generate()).thenReturn("012345");
        when(hasher.hash("new@example.com", "012345")).thenReturn("stored-hash");
        useCase.execute("New@Example.COM");
        verify(delivery).ensureAvailable();
        verify(store).consumeSourceAttempt();
        verify(store).issue("new@example.com", "stored-hash");
        verify(delivery).deliver("new@example.com", "012345");
    }

    @Test
    void rejectsInvalidAddressBeforeSideEffects() {
        for (String address : new String[] {"", "broken", "x".repeat(255)}) {
            assertThatThrownBy(() -> useCase.execute(address))
                    .isInstanceOf(AccountEmailCodeValidationException.class);
        }
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(AccountEmailCodeValidationException.class);
        verifyNoInteractions(store, generator, hasher, delivery);
    }

    @Test
    void missingDeliveryStopsBeforeLimitsAndInsertion() {
        doThrow(new AccountEmailCodeDeliveryException(true, false))
                .when(delivery).ensureAvailable();
        assertThatThrownBy(() -> useCase.execute("new@example.com"))
                .isInstanceOf(AccountEmailCodeDeliveryException.class);
        verifyNoInteractions(store, generator, hasher);
    }
}
