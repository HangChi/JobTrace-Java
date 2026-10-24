package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.application.AccountEmailCodeGenerator;
import com.jobtrace.identityaccess.application.BindAccountEmail;
import com.jobtrace.identityaccess.application.RequestAccountEmailCode;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.AccountEmailCodeStateException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.InvalidEmailBindingCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.when;

@SpringBootTest
class AccountEmailCodeDatabaseTest extends AccountEmailBindingFixture {

    @Autowired
    private RequestAccountEmailCode requestCode;

    @Autowired
    private BindAccountEmail bindEmail;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @MockitoBean
    private AccountEmailCodeGenerator generator;

    @BeforeEach
    void seedGeneratedCodes() {
        when(generator.generate()).thenReturn("123456", "654321");
    }

    @Test
    void issuedCodeIsStoredOnlyAsHashAndCanBeRedeemedByBinding() {
        requestCode.execute(OWNER, CURRENT_SESSION, "Fresh@Example.Test");
        var code = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq("fresh@example.test"), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject("""
                select code_hash from email_verification_codes
                where user_id=? and email='fresh@example.test'
                """, String.class, OWNER))
                .isEqualTo(codeHasher.hash(OWNER, "fresh@example.test", code.getValue()))
                .isNotEqualTo(code.getValue());
        assertThat(jdbc.queryForObject("""
                select expires_at>now() and expires_at<now()+interval '11 minutes'
                from email_verification_codes
                where user_id=? and email='fresh@example.test'
                """, Boolean.class, OWNER)).isTrue();
        bindEmail.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD,
                "fresh@example.test", code.getValue());
        assertThat(recoveryEmail(OWNER)).isEqualTo("fresh@example.test");
    }

    @Test
    void foreignSessionAndExistingOwnerEmailCannotIssueCode() {
        assertThatThrownBy(() -> requestCode.execute(OWNER, FOREIGN_SESSION,
                "fresh@example.test"))
                .isInstanceOf(AccountEmailCodeStateException.class);
        assertThatThrownBy(() -> requestCode.execute(OWNER, CURRENT_SESSION, FOREIGN_EMAIL))
                .isInstanceOf(AccountEmailConflictException.class);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email='fresh@example.test'
                """, Integer.class)).isZero();
        verify(delivery, never()).deliver(eq("fresh@example.test"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void oneMinuteCooldownCountsRejectedRepeatAndPreservesPriorCode() {
        requestCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL);
        assertThat(codeConsumed()).isTrue();
        int rows = jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email=? and user_id=?
                """, Integer.class, TARGET_EMAIL, OWNER);
        assertThatThrownBy(() -> requestCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL))
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email=? and user_id=?
                """, Integer.class, TARGET_EMAIL, OWNER)).isEqualTo(rows);
    }

    @Test
    void replacementAfterCooldownInvalidatesPreviousIssuedCode() {
        requestCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL);
        var first = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq(TARGET_EMAIL), first.capture());
        jdbc.update("""
                update auth_rate_limits set window_started_at=now()-interval '2 minutes'
                where action='email-code-cooldown'
                """);
        requestCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where user_id=? and email=? and consumed_at is null
                """, Integer.class, OWNER, TARGET_EMAIL)).isEqualTo(1);
        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                CURRENT_PASSWORD, TARGET_EMAIL, first.getValue()))
                .isInstanceOf(InvalidEmailBindingCodeException.class);
    }

    @Test
    void sixthAddressRequestWithinHourIsLimited() {
        for (int attempt = 0; attempt < 5; attempt++) {
            requestCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL);
            jdbc.update("""
                    update auth_rate_limits set window_started_at=now()-interval '2 minutes'
                    where action='email-code-cooldown'
                    """);
        }
        assertThatThrownBy(() -> requestCode.execute(OWNER, CURRENT_SESSION, TARGET_EMAIL))
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
        verify(delivery, times(5)).deliver(eq(TARGET_EMAIL),
                org.mockito.ArgumentMatchers.anyString());
    }
}
