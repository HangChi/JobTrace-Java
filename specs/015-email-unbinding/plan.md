# Implementation Plan: Account Email Unbinding

**Branch**: `codex/015-email-unbinding` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

## Summary

Expose only `DELETE /api/profile/email`. Reuse the 014 owner credential lookup and Better Auth password verifier, consume the legacy shared email-change rate limit, and clear recovery email plus pending codes in one owner-scoped transaction.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Existing Spring MVC, Spring Security, Spring JDBC, Micrometer, Better Auth-compatible scrypt hasher

**Storage**: Existing PostgreSQL `users`, `accounts`, `sessions`, `email_verification_codes`, and `consume_auth_rate_limit` function; no migration

**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo, Checkstyle

**Target Platform**: Existing modular monolith

**Project Type**: Web service

**Performance Goals**: Write p95 at or below 1 second with representative state

**Constraints**: Exact DELETE path, trusted bridge identity and current session, no leaked identity or password in diagnostics, atomic owner-scoped mutation

**Scale/Scope**: One authenticated command, no delivery or frontend work

## Constitution Check

- Maintainability: Reuse existing credential and hash components; add one application command and one persistence port/adapter.
- Tests: Unit, contract, security, integration, rollback, and representative performance coverage; run `./mvnw verify`.
- UX: Preserve established response/error copy; no frontend changes.
- Performance: Record reproducible p95; bounded metrics and no sensitive tags.
- Dependencies: No new dependency or deployment unit.

Post-design check: All gates satisfied by the selected design.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
  application/    UnbindAccountEmail, AccountEmailUnbindingStore
  domain/         AccountEmailUnbindingResult and safe failures
  infrastructure/ PostgresAccountEmailUnbindingStore
  web/            AccountEmailUnbindingController, request, metrics, no-store filter
src/test/java/com/jobtrace/identityaccess/ AccountEmailUnbinding*Test
src/test/resources/postgres/account-email-unbinding.sql
specs/015-email-unbinding/ contracts, data model, research, quickstart, tasks
docs/migration-slices/email-unbinding.md
```

**Structure Decision**: Follow the existing identity-access module's web → application → domain direction; infrastructure implements the application port.

## Complexity Tracking

No constitution exceptions or new architectural layers.
