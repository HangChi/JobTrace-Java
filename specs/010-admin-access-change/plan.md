# Implementation Plan: Administrator Access Changes

**Branch**: `codex/010-admin-access-change` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-admin-access-change/spec.md`

## Summary

Add the single administrator-authorized account-access command deferred by feature 009. Reuse the existing signed identity bridge and the legacy PostgreSQL `change_user_access_as` operation so optimistic concurrency, last-administrator protection, self-confirmation, serialized idempotency, session revocation, and append-only auditing remain atomic and have one authoritative implementation. Add validation, stable safe problems, bounded metrics, contract/integration/security/performance tests, and no production schema, frontend, deployment, routing, or CI/CD change.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC/Security/JDBC, Jackson, Micrometer

**Storage**: Existing PostgreSQL 17 users, sessions, and administration audit tables through the established security-definer command; Flyway remains disabled

**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers PostgreSQL, ArchUnit, JaCoCo, Checkstyle

**Target Platform**: Existing Linux-compatible Spring Boot service

**Project Type**: Modular monolith web service

**Performance Goals**: Access changes at or below 1 second p95 on a reproducible representative dataset

**Constraints**: Exact PATCH path and administrator assertion; opaque target IDs; strict body validation; serialized idempotency; private no-store; no duplicated access policy; no schema/frontend/CI/CD/deployment/routing work

**Scale/Scope**: One command route, four actions, one application use case, one write port/adapter, one existing database function

## Constitution Check

*GATE: Passed before research and passed again after design.*

- **Maintainability**: Pass. One command object, use case, port, JDBC adapter, and controller method extend the existing administration context. Security policy remains in the existing atomic database operation rather than being duplicated.
- **Testing**: Pass. Unit validation and orchestration, PostgreSQL integration/concurrency/idempotency, HTTP/security, contract, architecture, read-regression, and performance tests are planned with existing 80% line/branch gates.
- **Consistent UX**: Pass. Established action names, Chinese validation/conflict messages, safe latest-state recovery, request IDs, and no-store behavior are preserved. No frontend is changed.
- **Performance**: Pass. A reproducible write p95 budget of 1 second and bounded database interaction are measured.
- **Delivery**: Pass for the requested code scope. No production activation is attempted; rollback remains routing to the established service. CI/CD files are explicitly excluded by user direction.

## Project Structure

### Documentation (this feature)

```text
specs/010-admin-access-change/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/jobtrace/admin/
├── domain/
├── application/
├── infrastructure/
└── web/

src/main/java/com/jobtrace/identityaccess/web/BridgeAuthenticationFilter.java
src/test/java/com/jobtrace/admin/
src/test/java/com/jobtrace/identityaccess/
src/test/resources/postgres/admin-access-change.sql
src/test/resources/contracts/admin/access-change.legacy.json
```

**Structure Decision**: Extend the feature 009 administration context and shared bridge. The application layer owns orchestration, the domain owns input/result vocabulary, infrastructure alone calls the established database command, and web code owns transport validation and problems.

## Design Sequence

1. Freeze the legacy body, success, conflict, authorization, idempotency, audit, and session-revocation contracts.
2. Add immutable command/action/result types, validation, domain failures, and a write port.
3. Add a JDBC adapter that invokes only `change_user_access_as` and parses its JSON result.
4. Add the application use case, exact PATCH bridge allowlist, controller operation, no-store errors, and bounded metrics.
5. Verify successful actions, every protected rejection, identical and conflicting retries, concurrent retries, dependency failures, architecture, read regressions, and performance.
6. Update migration contracts and local evidence without changing CI/CD or deployment.

## Complexity Tracking

No constitution violations require justification. The database operation is an existing policy boundary, not a new architectural layer or dependency.
