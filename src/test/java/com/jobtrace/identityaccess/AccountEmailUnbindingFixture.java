package com.jobtrace.identityaccess;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

abstract class AccountEmailUnbindingFixture extends AccountPasswordDatabaseTest {

    protected static final String OWNER_EMAIL = "owner-recovery@example.test";
    protected static final String FOREIGN_EMAIL = "foreign-recovery@example.test";

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void seedEmailState() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "postgres/account-email-unbinding.sql")).execute(dataSource);
        jdbc.execute("""
                create or replace function consume_auth_rate_limit(
                  request_key_hash text,request_action text,
                  request_limit integer,request_window_seconds integer
                ) returns boolean language plpgsql as $$
                declare allowed boolean;
                begin
                  insert into auth_rate_limits(key_hash,action,window_started_at,request_count)
                  values(request_key_hash,request_action,now(),1)
                  on conflict(key_hash,action) do update set
                    window_started_at=case
                      when auth_rate_limits.window_started_at
                        <= now()-make_interval(secs => request_window_seconds)
                      then now() else auth_rate_limits.window_started_at end,
                    request_count=case
                      when auth_rate_limits.window_started_at
                        <= now()-make_interval(secs => request_window_seconds)
                      then 1 else auth_rate_limits.request_count+1 end
                  returning request_count <= request_limit into allowed;
                  return allowed;
                end $$
                """);
        jdbc.update("""
                update users set recovery_email=?,recovery_email_verified_at=now(),
                  email_verified=true where id=?
                """, OWNER_EMAIL, OWNER);
        jdbc.update("""
                update users set recovery_email=?,recovery_email_verified_at=now(),
                  email_verified=true where id=?
                """, FOREIGN_EMAIL, OTHER_OWNER);
        insertCode("00000000-0000-0000-0000-000000000001", OWNER, OWNER_EMAIL);
        insertCode("00000000-0000-0000-0000-000000000002", OWNER, OWNER_EMAIL);
        insertCode("00000000-0000-0000-0000-000000000003", OTHER_OWNER, FOREIGN_EMAIL);
    }

    protected void insertCode(String id, String owner, String email) {
        jdbc.update("""
                insert into email_verification_codes(id,email,purpose,user_id,code_hash,expires_at)
                values (?::uuid,?,'email_binding',?,'fixed-hash','2099-01-01T00:00:00Z')
                """, id, email, owner);
    }

    protected String recoveryEmail(String owner) {
        return jdbc.queryForObject("select recovery_email from users where id=?",
                String.class, owner);
    }

    protected boolean emailVerified(String owner) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select email_verified from users where id=?", Boolean.class, owner));
    }

    protected List<Boolean> consumedCodes(String owner) {
        return jdbc.queryForList("""
                select consumed_at is not null from email_verification_codes
                where user_id=? order by id
                """, Boolean.class, owner);
    }
}
