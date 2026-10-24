# Implementation Plan: Complete Account Authentication

**Branch**: `codex/022-account-authentication` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

## Summary

Complete account authentication in the existing Java monolith: validate shared-format browser sessions against current database state; preserve signed bridge access; expose safe session/profile reads; implement compatible password recovery and validated avatar upload. Browser writes must pass a same-origin boundary, and reset tokens must be consumed atomically. No schema or production-routing ownership changes.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Spring Web/Security/JDBC, JDK cryptography and HTTP client, existing mail delivery and cookie codecs

**Storage**: Legacy PostgreSQL users, sessions, accounts, verification_tokens; Tencent COS for avatar objects

**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo; fixed legacy compatibility fixtures

**Target Platform**: Existing Spring Boot modular monolith

**Project Type**: Backend web-service migration stage

**Performance Goals**: Protected reads <= 500 ms p95 and interactive writes <= 1 s p95 under representative local load

**Constraints**: Preserve signed bridge, fail closed on storage failure, no public identity headers, no schema ownership transfer, no frontend or CI/CD changes

**Scale/Scope**: Session authentication and profile read; password-reset request/callback/consume; avatar upload

## Constitution Check

- Maintainability: add session verification behind an application port and retain distinct browser/bridge credential types; keep web, application, domain and infrastructure dependency direction.
- Testing: cover cookie tampering, expiry, revocation, disabled accounts, stale roles, origin failures, token races, reset delivery and image signature checks with at least 80% changed-code line and branch coverage.
- UX/accessibility: frontend unchanged; preserve old recovery response and reset link journey, with explicit non-cacheable errors.
- Performance: bound per-request database reads and upload size; no background work on interactive read paths.
- Risk/rollback: old service remains the production owner; revert this route set if activation later fails, without changing schema.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
├── application/       # session, recovery and avatar use cases and ports
├── domain/            # verified browser identity and recovery rules
├── infrastructure/    # existing-schema SQL, mail/COS adapters, cookie codec
└── web/               # exact routes and security filters
src/main/java/com/jobtrace/shared/security/ # dual credential security chain
src/test/java/com/jobtrace/identityaccess/  # unit, HTTP, DB and compatibility tests
specs/022-account-authentication/          # design and acceptance evidence
```

**Structure Decision**: Extend the existing identity-access context; do not introduce another service, JPA mapping, or new schema.

## Complexity Tracking

No constitution exception. Dual credential support is necessary because legacy signed bridge calls and new browser calls coexist; they remain separately verified and attributable.
