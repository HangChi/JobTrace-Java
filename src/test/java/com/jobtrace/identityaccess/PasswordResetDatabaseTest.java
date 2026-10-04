package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.identityaccess.application.RequestPasswordReset;
import com.jobtrace.identityaccess.application.ResetPassword;
import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.infrastructure.PostgresPasswordResetStore;
import com.jobtrace.testing.PostgresIntegrationTest;
import javax.sql.DataSource;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@SpringBootTest
class PasswordResetDatabaseTest extends PostgresIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private PasswordHasher hasher;
    private JdbcTemplate jdbc;
    private PostgresPasswordResetStore store;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("drop table if exists verification_tokens, sessions, accounts, users cascade");
        jdbc.execute("""
                create table users(id text primary key,recovery_email text,
                  recovery_email_verified_at timestamptz,disabled boolean not null default false)
                """);
        jdbc.execute("""
                create table accounts(id text primary key,user_id text not null,
                  provider_id text not null,password text,updated_at timestamptz default now())
                """);
        jdbc.execute("""
                create table sessions(id text primary key,user_id text not null)
                """);
        jdbc.execute("""
                create table verification_tokens(id text primary key,identifier text not null,
                  value text not null,expires_at timestamptz not null)
                """);
        jdbc.update("insert into users values ('owner','owner@example.test',now(),false)");
        jdbc.update("insert into users values ('other','other@example.test',now(),false)");
        jdbc.update("insert into accounts(id,user_id,provider_id,password) values"
                + "('credential-owner','owner','credential','old'),"
                + "('credential-other','other','credential','other-old')");
        jdbc.update("insert into sessions values ('s1','owner'),('s2','owner'),('s3','other')");
        store = new PostgresPasswordResetStore(new NamedParameterJdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void consumesOnceAndRevokesOnlyTheOwnersSessions() {
        String identifier = RequestPasswordReset.identifier("abcdefghijklmnopqrstuvwx");
        store.issue("owner", identifier);
        assertThat(store.valid(identifier)).isTrue();
        assertThat(store.consumeAndReplace(identifier, "new-hash")).isTrue();
        assertThat(store.consumeAndReplace(identifier, "second-hash")).isFalse();
        assertThat(jdbc.queryForObject("select password from accounts where user_id='owner'",
                String.class)).isEqualTo("new-hash");
        assertThat(jdbc.queryForObject("select password from accounts where user_id='other'",
                String.class)).isEqualTo("other-old");
        assertThat(jdbc.queryForList("select id from sessions", String.class))
                .containsExactly("s3");
    }

    @Test
    void expiredAndMissingCredentialsLeaveTheGrantAndSessionsUnchanged() {
        String identifier = RequestPasswordReset.identifier("abcdefghijklmnopqrstuvwx");
        store.issue("owner", identifier);
        jdbc.update("update verification_tokens set expires_at=now()-interval '1 second'");
        assertThat(store.consumeAndReplace(identifier, "new-hash")).isFalse();
        assertThat(jdbc.queryForObject("select password from accounts where user_id='owner'",
                String.class)).isEqualTo("old");
        jdbc.update("update verification_tokens set expires_at=now()+interval '1 hour'");
        jdbc.update("delete from accounts where user_id='owner'");
        assertThat(store.consumeAndReplace(identifier, "new-hash")).isFalse();
        assertThat(store.valid(identifier)).isTrue();
        assertThat(jdbc.queryForList("select id from sessions order by id", String.class))
                .containsExactly("s1", "s2", "s3");
    }

    @Test
    void representativeResetWritesMeetP95Budget() {
        ResetPassword reset = new ResetPassword(store, hasher);
        long[] elapsed = new long[20];
        for (int index = 0; index < elapsed.length; index++) {
            String token = "%024d".formatted(index);
            store.issue("owner", RequestPasswordReset.identifier(token));
            long started = System.nanoTime();
            reset.execute(token, "New-Password");
            elapsed[index] = (System.nanoTime() - started) / 1_000_000;
        }
        Arrays.sort(elapsed);
        assertThat(elapsed[18]).isLessThanOrEqualTo(1_000);
        System.out.printf("022 password-reset write p95 ms: %d%n", elapsed[18]);
    }
}
