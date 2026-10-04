package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.LogoutSession;
import com.jobtrace.identityaccess.application.LogoutSessionStore;
import org.junit.jupiter.api.Test;

class LogoutSessionTest {
    private final LogoutSessionStore store = mock(LogoutSessionStore.class);
    private final LogoutSession logout = new LogoutSession(store);

    @Test
    void ignoresMissingTokenAndDeletesOnlyVerifiedValue() {
        assertThat(logout.execute(null)).isFalse();
        assertThat(logout.execute(" ")).isFalse();
        verifyNoInteractions(store);
        when(store.deleteByToken("token-one")).thenReturn(true);
        assertThat(logout.execute("token-one")).isTrue();
        verify(store).deleteByToken("token-one");
    }
}
