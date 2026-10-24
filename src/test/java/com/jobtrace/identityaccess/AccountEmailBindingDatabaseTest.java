package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.BindAccountEmail;
import com.jobtrace.identityaccess.domain.AccountEmailBindingInvalidPasswordException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingStateException;
import com.jobtrace.identityaccess.domain.AccountEmailConflictException;
import com.jobtrace.identityaccess.domain.AccountEmailRateLimitedException;
import com.jobtrace.identityaccess.domain.InvalidEmailBindingCodeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountEmailBindingDatabaseTest extends AccountEmailBindingFixture {

    @Autowired
    private BindAccountEmail bindEmail;

    @Test
    void bindsOnlyOwnerAndConsumesExactlyTheMatchingCode() {
        var result = bindEmail.execute(OWNER, CURRENT_SESSION, CURRENT_PASSWORD,
                "NEW@EXAMPLE.TEST", CORRECT_CODE);

        assertThat(result.email()).isEqualTo(TARGET_EMAIL);
        assertThat(result.emailVerified()).isTrue();
        assertThat(recoveryEmail(OWNER)).isEqualTo(TARGET_EMAIL);
        assertThat(emailVerified(OWNER)).isTrue();
        assertThat(codeConsumed()).isTrue();
        assertThat(recoveryEmail(OTHER_OWNER)).isEqualTo(FOREIGN_EMAIL);
        assertThat(consumedCodes(OTHER_OWNER)).containsExactly(false);
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                CURRENT_PASSWORD, TARGET_EMAIL, CORRECT_CODE))
                .isInstanceOf(InvalidEmailBindingCodeException.class);
    }

    @Test
    void wrongCodeAttemptsPersistAndExhaustAtFive() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                    CURRENT_PASSWORD, TARGET_EMAIL, "000000"))
                    .isInstanceOf(InvalidEmailBindingCodeException.class);
            assertThat(codeAttempts()).isEqualTo(attempt);
        }
        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                CURRENT_PASSWORD, TARGET_EMAIL, CORRECT_CODE))
                .isInstanceOf(AccountEmailRateLimitedException.class);
        assertThat(codeConsumed()).isFalse();
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
    }

    @Test
    void wrongPasswordAndForeignSessionDoNotTouchCode() {
        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                "wrong", TARGET_EMAIL, CORRECT_CODE))
                .isInstanceOf(AccountEmailBindingInvalidPasswordException.class);
        assertThatThrownBy(() -> bindEmail.execute(OWNER, FOREIGN_SESSION,
                CURRENT_PASSWORD, TARGET_EMAIL, CORRECT_CODE))
                .isInstanceOf(AccountEmailBindingStateException.class);
        assertThat(codeAttempts()).isZero();
        assertThat(codeConsumed()).isFalse();
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
    }

    @Test
    void uniqueEmailConflictRollsBackValidCodeConsumption() {
        jdbc.update("update email_verification_codes set email=?,code_hash=? where id=?::uuid",
                FOREIGN_EMAIL, codeHasher.hash(OWNER, FOREIGN_EMAIL, CORRECT_CODE), CODE_ID);

        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                CURRENT_PASSWORD, FOREIGN_EMAIL, CORRECT_CODE))
                .isInstanceOf(AccountEmailConflictException.class);
        assertThat(codeConsumed()).isFalse();
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(recoveryEmail(OTHER_OWNER)).isEqualTo(FOREIGN_EMAIL);
    }

    @Test
    void foreignAndExpiredCodesCannotBind() {
        jdbc.update("update email_verification_codes set user_id=? where id=?::uuid",
                OTHER_OWNER, CODE_ID);
        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                CURRENT_PASSWORD, TARGET_EMAIL, CORRECT_CODE))
                .isInstanceOf(InvalidEmailBindingCodeException.class);
        jdbc.update("update email_verification_codes set user_id=?,expires_at=now()-interval '1 second'"
                + " where id=?::uuid", OWNER, CODE_ID);
        assertThatThrownBy(() -> bindEmail.execute(OWNER, CURRENT_SESSION,
                CURRENT_PASSWORD, TARGET_EMAIL, CORRECT_CODE))
                .isInstanceOf(InvalidEmailBindingCodeException.class);
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(codeConsumed()).isFalse();
    }
}
