# Implementation Plan: Account Email Binding

**Branch**: `codex/016-email-binding` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

## Summary

Expose only `PATCH /api/profile/email` for a verified owner. Reuse current-session and password verification, the shared email-change limiter, and the legacy code-attempt database function. Compute the same HMAC code representation using the configured legacy secret. Perform successful code consumption and email update in one transaction; commit wrong-code attempt increments without changing the email.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Existing Spring MVC, Spring Security, Spring JDBC, Micrometer, JCA HmacSHA256

**Storage**: Existing PostgreSQL users, accounts, sessions, email_verification_codes, and legacy rate/code functions; no migration

**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, OpenAPI parser, JaCoCo, Checkstyle

**Target Platform**: Existing modular monolith

**Project Type**: Web service

**Performance Goals**: Write p95 at or below one second

**Constraints**: Exact PATCH path, signed owner assertion and active owner session, shared HMAC secret, atomic success, durable wrong-code attempts, no personal/secret diagnostics

**Scale/Scope**: One authenticated command; legacy service retains code issuance and delivery

## Constitution Check

- Maintainability: Reuse password and rate-limit components, add one binding use case and one persistence adapter.
- Testing: Unit, database, HTTP, security, compatibility, failure, and representative performance tests; full `./mvnw verify`.
- UX: Preserve normalized email, verified flag, and established errors; no frontend edits.
- Performance: Measure representative p95 and keep bounded metrics.
- Dependencies: JCA is part of the JDK; no new library or deployment unit.

Post-design check: No constitution exception needed.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
  application/    BindAccountEmail, AccountEmailBindingStore, shared rate-limit port
  domain/         binding result and safe failures
  infrastructure/ legacy-compatible HMAC and PostgreSQL binding adapter
  web/            PATCH controller, request parser, no-store handling, metrics
src/test/java/com/jobtrace/identityaccess/ binding tests
src/test/resources/postgres/ binding fixtures
specs/016-email-binding/ design and test evidence
docs/migration-slices/email-binding.md
```

**Structure Decision**: Keep web → application → domain, with infrastructure implementing ports. No frontend or schema change.

## Complexity Tracking

No additional architectural layer or exception.
