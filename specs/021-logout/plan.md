# Implementation Plan: Compatible Current-Session Logout

**Branch**: `codex/021-logout` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/021-logout/spec.md`

## Summary

Add exact anonymous `POST /api/auth/logout` with the existing same-origin check. Verify the signed Better Auth session cookie using the same secret and constant-time comparison, delete only its token from the existing sessions table, and expire the established cookies (including cached-session chunks). Preserve the old idempotent 204 and best-effort response for persistence failures. Keep all other Java API authentication on the signed bridge.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Spring Web/Security/JDBC, JDK HMAC and URL decoding, existing origin guard and cookie configuration

**Storage**: Existing PostgreSQL sessions; no schema change

**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo, fixed cookie signatures

**Target Platform**: Existing Spring Boot modular monolith

**Project Type**: Backend web-service migration slice

**Performance Goals**: Representative logout p95 at most one second

**Constraints**: No browser cookie authorization for other Java APIs; no CI/CD or production routing changes

**Scale/Scope**: One public command route, verified cookie codec, one-token session deletion and cookie cleanup

## Constitution Check

- Maintainability: extend the 020 cookie boundary; isolate deletion behind an application port and retain the existing origin guard.
- Testing: cover absent, valid, tampered, duplicate, malformed and stale cookies; secure/plain naming; session isolation; origin and DB failure; changed-code coverage at least 80% line and branch.
- UX/accessibility: no frontend changes; preserve empty 204 success and established cookie security attributes.
- Performance: representative p95 <= one second; bounded outcomes without tokens or personal identifiers.
- Rollback: old service remains the production route owner; revert only this POST path if activated later.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
├── application/       # current-session deletion port and use case
├── infrastructure/    # signed cookie verification and PostgreSQL token deletion
└── web/               # exact logout route, cookie expiry and bounded metrics
src/main/java/com/jobtrace/shared/security/ # exact public POST allowance
src/test/java/com/jobtrace/identityaccess/  # codec, contract, security, DB and p95 tests
specs/021-logout/                    # decisions, contract and verification evidence
```

**Structure Decision**: Extend the existing identity-access bounded context. A signed browser cookie may select only the logout session, never authorize a protected Java route.

## Complexity Tracking

No constitution exceptions. Strict cookie parsing is required to avoid ambiguous token selection and keep cross-runtime HMAC compatibility.
