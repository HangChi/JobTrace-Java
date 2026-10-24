# Implementation Plan: Account Password Change

**Branch**: `codex/014-password-change` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014-password-change/spec.md`

## Summary

Add only `POST /api/profile/password`. Parse and validate the two password fields at the web/application boundary, derive the owner exclusively from the signed bridge principal, require bounded current-session metadata, verify and create Better Auth-compatible scrypt hashes, and perform a conditional credential replacement plus other-session revocation in one database transaction.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC/Security/JDBC, Micrometer, Bouncy Castle 1.86 for the maintained scrypt primitive

**Storage**: Existing PostgreSQL 17 `accounts` and `sessions` tables; no migration

**Testing**: JUnit 5, Spring MockMvc, AssertJ, Testcontainers PostgreSQL, JaCoCo, ArchUnit, Swagger Parser

**Target Platform**: Linux Spring Boot server embedded in the existing modular monolith

**Project Type**: Backend web service with an unchanged embedded frontend artifact

**Performance Goals**: Password changes complete within 1 second p95 for the representative fixture

**Constraints**: Better Auth scrypt compatibility; constant-time hash comparison; credential replacement and session revocation are atomic; current session is preserved; no password/hash/session data in diagnostics; no schema, frontend, deployment, traffic, or CI/CD changes

**Scale/Scope**: One exact owner-scoped POST route, one credential account, and the owner's current session set

## Constitution Check

- Maintainability: one cohesive application use case and two narrow ports keep cryptography and persistence behind explicit boundaries.
- Testing: unit, compatibility-vector, database, contract, authorization, atomicity, failure, observability, and performance tests cover the behavior change.
- User experience: established validation codes, field attribution, Chinese success/error copy, and no-store responses remain stable; no UI changes are made.
- Performance: the one-second write budget is measured with the production scrypt parameters and representative storage.
- Security/operations: signed identity and current-session ownership are both required; comparisons are constant-time; diagnostics contain only bounded outcomes.
- Dependency rationale: Java SE has no scrypt primitive. Bouncy Castle is a maintained cryptographic provider, avoids a custom security implementation, and is used only for the exact Better Auth parameters.
- Scope: reset, registration, login/logout, email, session issuance, frontend, deployment, routing, production traffic, and CI/CD remain unchanged.

Post-design re-check: all gates pass; no exception or additional deployment unit is required.

## Project Structure

### Documentation (this feature)

```text
specs/014-password-change/
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
pom.xml
src/main/java/com/jobtrace/identityaccess/
├── application/
├── domain/
├── infrastructure/
└── web/
src/test/java/com/jobtrace/identityaccess/
src/test/resources/postgres/
docs/migration-slices/
```

**Structure Decision**: Extend the existing identity-access bounded context and preserve `web -> application -> domain`, with infrastructure implementing application ports. No frontend or schema project is changed.
