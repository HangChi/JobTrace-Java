package com.jobtrace.identityaccess;

import com.jobtrace.identityaccess.application.EmailVerificationCodeHasher;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties =
        "jobtrace.email-binding.verification-secret=0123456789abcdef0123456789abcdef")
abstract class AccountEmailBindingFixture extends AccountEmailUnbindingFixture {

    protected static final String TARGET_EMAIL = "new@example.test";
    protected static final String CORRECT_CODE = "123456";
    protected static final String CODE_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    protected EmailVerificationCodeHasher codeHasher;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void seedBindingState() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/account-email-binding.sql")).execute(dataSource);
        jdbc.execute("""
                create or replace function verify_email_code_attempt(
                  target_email text,target_purpose text,target_user_id text,
                  submitted_code_hash text
                ) returns table(code_id uuid,matched boolean)
                language plpgsql as $$
                declare candidate email_verification_codes%rowtype;
                begin
                  select verification.* into candidate
                  from email_verification_codes verification
                  where lower(verification.email)=lower(target_email)
                    and verification.purpose=target_purpose
                    and verification.user_id is not distinct from target_user_id
                    and verification.consumed_at is null
                    and verification.expires_at>now()
                    and verification.attempt_count<5
                  order by verification.created_at desc limit 1 for update;
                  if not found then return; end if;
                  code_id:=candidate.id;
                  matched:=candidate.code_hash=submitted_code_hash;
                  if matched then
                    update email_verification_codes set consumed_at=now()
                    where id=candidate.id;
                  else
                    update email_verification_codes
                    set attempt_count=attempt_count+1 where id=candidate.id;
                  end if;
                  return next;
                end $$
                """);
        jdbc.update("""
                update email_verification_codes set email=?,code_hash=?,
                  created_at=now()+interval '1 second' where id=?::uuid
                """, TARGET_EMAIL, codeHasher.hash(OWNER, TARGET_EMAIL, CORRECT_CODE),
                CODE_ID);
        jdbc.update("""
                update email_verification_codes set consumed_at=now()
                where id='00000000-0000-0000-0000-000000000002'::uuid
                """);
    }

    protected boolean codeConsumed() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select consumed_at is not null from email_verification_codes
                where id=?::uuid
                """, Boolean.class, CODE_ID));
    }

    protected int codeAttempts() {
        return jdbc.queryForObject("""
                select attempt_count from email_verification_codes where id=?::uuid
                """, Integer.class, CODE_ID);
    }
}
