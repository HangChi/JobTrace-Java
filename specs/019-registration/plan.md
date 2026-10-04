# Implementation Plan: Verified Account Registration

**Branch**: `codex/019-registration` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/019-registration/spec.md`

## Summary

Add only anonymous `POST /api/auth/register` to Java. Preserve the old validation, origin, five-per-minute source limit, optional challenge, registration-code HMAC and atomic attempt function. Persist a Better Auth-compatible user and credential together in one database transaction; do not issue a session. The old service retains production routing and login.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Spring Web, Spring Security, Spring JDBC, existing scrypt adapter

**Storage**: Existing PostgreSQL users, accounts, email_verification_codes and auth_rate_limits; no migration

**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo

**Target Platform**: Existing Spring Boot modular monolith

**Project Type**: Backend web-service migration slice

**Performance Goals**: Registration write p95 at most one second excluding external challenge latency

**Constraints**: Existing API/error compatibility; no login or session; no CI/CD changes

**Scale/Scope**: One public command route and its application, persistence and challenge adapters

## Constitution Check

- Maintainability: reuse registration HMAC/origin guard/password hasher and the existing PostgreSQL function; one transaction for the user/credential pair.
- Tests: unit validation, HTTP/security, PostgreSQL interoperability and challenge tests; at least 80% line and branch coverage for changed classes.
- UX/accessibility: no frontend change; retain existing response copy and field-specific feedback.
- Performance: representative p95 write test under the one-second budget; bounded metrics without PII.
- Deployment: old service remains route owner, allowing single-path rollback.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
├── application/       # registration validation and orchestration
├── domain/            # stable registration failures
├── infrastructure/    # code attempt, user/credential persistence, challenge
└── web/               # anonymous route, request parsing and bounded metrics
src/main/java/com/jobtrace/shared/security/ # exact public-route exception
src/test/java/com/jobtrace/identityaccess/  # focused unit, contract, DB and security tests
specs/019-registration/                    # contract, decisions and evidence
```

**Structure Decision**: Extend the existing identity-access bounded context; do not add a deployment unit or database schema.

## Complexity Tracking

No constitution exceptions. The challenge adapter is isolated because it is a configurable external security boundary; tests substitute it without network access.
