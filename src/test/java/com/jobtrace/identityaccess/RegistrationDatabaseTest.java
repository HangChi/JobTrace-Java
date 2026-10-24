package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.application.RegisterAccount;
import com.jobtrace.identityaccess.application.RegistrationEmailCodeHasher;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.InvalidRegistrationCodeException;
import com.jobtrace.identityaccess.domain.RegistrationConflictException;
import com.jobtrace.identityaccess.infrastructure.PostgresEmailCodeRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class RegistrationDatabaseTest extends AccountEmailBindingFixture {

    private static final String EMAIL = "registrant@example.test";
    private static final String CODE_ID = "00000000-0000-0000-0000-000000000019";

    @Autowired
    private RegisterAccount registration;

    @Autowired
    private RegistrationEmailCodeHasher registrationHasher;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private PostgresEmailCodeRateLimiter limiter;

    @BeforeEach
    void seedRegistrationSchemaAndCode() {
        jdbc.execute("alter table users add column username text unique");
        jdbc.execute("alter table users add column display_username text");
        jdbc.execute("alter table users add column role text not null default 'user'");
        jdbc.update("""
                insert into email_verification_codes
                  (id,email,purpose,user_id,code_hash,expires_at)
                values (?::uuid,?,'registration',null,?,now()+interval '10 minutes')
                """, CODE_ID, EMAIL, registrationHasher.hash(EMAIL, "123456"));
    }

    @Test
    void createsCompatibleUserAndCredentialWithoutSession() {
        registration.execute(input("new_user", EMAIL, "123456"));

        assertThat(jdbc.queryForObject("""
                select count(*) from users
                where username='new_user' and display_username='new_user'
                  and display_name='New User'
                  and email='new_user@users.jobtrace.local'
                  and recovery_email=? and recovery_email_verified_at is not null
                  and email_verified=true and role='user'
                """, Integer.class, EMAIL)).isEqualTo(1);
        String passwordHash = jdbc.queryForObject("""
                select a.password from accounts a join users u on u.id=a.user_id
                where u.username='new_user' and a.provider_id='credential'
                  and a.account_id=u.id
                """, String.class);
        assertThat(passwordHash).isNotEqualTo("Password123!");
        assertThat(passwordHasher.matches("Password123!", passwordHash)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(3);
        assertThat(consumed()).isTrue();
        assertThatThrownBy(() -> registration.execute(input("second_user", EMAIL, "123456")))
                .isInstanceOf(InvalidRegistrationCodeException.class);
    }

    @Test
    void wrongCodeUsesLegacyAttemptLimit() {
        for (int index = 0; index < 5; index++) {
            assertThatThrownBy(() -> registration.execute(input("new_user", EMAIL, "999999")))
                    .isInstanceOf(InvalidRegistrationCodeException.class);
        }
        assertThat(jdbc.queryForObject("""
                select attempt_count from email_verification_codes where id=?::uuid
                """, Integer.class, CODE_ID)).isEqualTo(5);
        assertThatThrownBy(() -> registration.execute(input("new_user", EMAIL, "123456")))
                .isInstanceOf(InvalidRegistrationCodeException.class);
        assertThat(jdbc.queryForObject("select count(*) from users where username='new_user'",
                Integer.class)).isZero();
    }

    @Test
    void duplicateUsernameConsumesCodeButCreatesNoAccount() {
        jdbc.update("update users set username='new_user' where id=?", OWNER);
        assertThatThrownBy(() -> registration.execute(input("new_user", EMAIL, "123456")))
                .isInstanceOf(RegistrationConflictException.class)
                .satisfies(error -> assertThat(((RegistrationConflictException) error).field())
                        .isEqualTo("username"));
        assertThat(consumed()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void duplicateRecoveryEmailReturnsEmailField() {
        jdbc.update("update users set recovery_email=? where id=?", EMAIL, OWNER);
        assertThatThrownBy(() -> registration.execute(input("new_user", EMAIL, "123456")))
                .isInstanceOf(RegistrationConflictException.class)
                .satisfies(error -> assertThat(((RegistrationConflictException) error).field())
                        .isEqualTo("email"));
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void occupiedInternalAuthenticationEmailMapsToUsernameConflict() {
        jdbc.update("update users set email='new_user@users.jobtrace.local' where id=?", OWNER);
        assertThatThrownBy(() -> registration.execute(input("new_user", EMAIL, "123456")))
                .isInstanceOf(RegistrationConflictException.class)
                .satisfies(error -> assertThat(((RegistrationConflictException) error).field())
                        .isEqualTo("username"));
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void failedCredentialInsertRollsBackNewUser() {
        jdbc.execute("""
                alter table accounts add constraint reject_new_registration
                check (user_id in ('password-owner','password-other'))
                """);
        assertThatThrownBy(() -> registration.execute(input("new_user", EMAIL, "123456")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class))
                .isEqualTo(2);
        assertThat(consumed()).isTrue();
    }

    @Test
    void registrationSourceLimitRejectsSixthAttempt() {
        for (int index = 0; index < 5; index++) {
            limiter.consumeRegistrationAttempt();
        }
        assertThatThrownBy(limiter::consumeRegistrationAttempt)
                .isInstanceOf(AccountEmailCodeRateLimitedException.class);
    }

    private boolean consumed() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select consumed_at is not null from email_verification_codes where id=?::uuid
                """, Boolean.class, CODE_ID));
    }

    private static RegisterAccount.Input input(String username, String email, String code) {
        return new RegisterAccount.Input(username, email, code, "Password123!", "New User");
    }
}
