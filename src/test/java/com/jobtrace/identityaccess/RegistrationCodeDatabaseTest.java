package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.application.AccountEmailCodeGenerator;
import com.jobtrace.identityaccess.application.RequestAccountEmailCode;
import com.jobtrace.identityaccess.application.RequestRegistrationEmailCode;
import com.jobtrace.identityaccess.application.RegistrationEmailCodeHasher;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.RegistrationEmailConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RegistrationCodeDatabaseTest extends AccountEmailBindingFixture {

    @Autowired
    private RequestRegistrationEmailCode requestCode;

    @Autowired
    private RequestAccountEmailCode requestBindingCode;

    @Autowired
    private RegistrationEmailCodeHasher registrationHasher;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @MockitoBean
    private AccountEmailCodeGenerator generator;

    @BeforeEach
    void fixedCodes() {
        when(generator.generate()).thenReturn("123456", "654321");
    }

    @Test
    void storesNullOwnerPurposeBoundHashAndDeliversCode() {
        requestCode.execute("Fresh@Example.Test");
        var code = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq("fresh@example.test"), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject("""
                select code_hash from email_verification_codes
                where email='fresh@example.test' and purpose='registration'
                """, String.class))
                .isEqualTo(registrationHasher.hash("fresh@example.test", code.getValue()))
                .isNotEqualTo(code.getValue());
        assertThat(jdbc.queryForObject("""
                select user_id is null and expires_at>now()
                  and expires_at<now()+interval '11 minutes'
                from email_verification_codes
                where email='fresh@example.test' and purpose='registration'
                """, Boolean.class)).isTrue();
    }

    @Test
    void replacementConsumesOnlyRegistrationCodes() {
        requestCode.execute(TARGET_EMAIL);
        jdbc.update("""
                update auth_rate_limits set window_started_at=now()-interval '2 minutes'
                where action='email-code-cooldown'
                """);
        requestCode.execute(TARGET_EMAIL);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email=? and purpose='registration' and consumed_at is null
                """, Integer.class, TARGET_EMAIL)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email=? and purpose='registration' and consumed_at is not null
                """, Integer.class, TARGET_EMAIL)).isEqualTo(1);
        assertThat(codeConsumed()).isFalse();
    }

    @Test
    void boundAddressConflictsWithoutDelivery() {
        assertThatThrownBy(() -> requestCode.execute(FOREIGN_EMAIL))
                .isInstanceOf(RegistrationEmailConflictException.class);
        verify(delivery, never()).deliver(anyString(), anyString());
    }

    @Test
    void addressHourlyLimitAppliesAfterFiveIssuedCodes() {
        for (int index = 0; index < 5; index++) {
            requestCode.execute(TARGET_EMAIL);
            jdbc.update("""
                    update auth_rate_limits set window_started_at=now()-interval '2 minutes'
                    where action='email-code-cooldown'
                    """);
        }
        assertThatThrownBy(() -> requestCode.execute(TARGET_EMAIL))
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
        verify(delivery, times(5)).deliver(eq(TARGET_EMAIL), anyString());
    }

    @Test
    void cooldownRejectsImmediateRepeatAndStillCountsAddressAttempt() {
        requestCode.execute(TARGET_EMAIL);
        assertThatThrownBy(() -> requestCode.execute(TARGET_EMAIL))
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
        assertThat(jdbc.queryForObject("""
                select request_count from auth_rate_limits
                where action='email-code-address'
                """, Integer.class)).isEqualTo(2);
        verify(delivery).deliver(eq(TARGET_EMAIL), anyString());
    }

    @Test
    void addressLimitIsSharedWithBindingCodeRequests() {
        requestBindingCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL);
        assertThatThrownBy(() -> requestCode.execute(TARGET_EMAIL))
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
    }
}
