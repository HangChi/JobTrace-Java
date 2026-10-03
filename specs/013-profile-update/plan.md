# Implementation Plan: Account Profile Update

**Branch**: `codex/013-profile-update` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

## Summary

Add `PATCH /api/profile` for the established display-name and hosted-avatar update. Normalize the request at the web boundary, derive the owner only from the signed bridge principal, execute one owner-scoped update returning the unchanged recovery-email projection, and preserve safe errors and bounded diagnostics.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Security, Spring JDBC, Micrometer

**Storage**: Existing PostgreSQL 17 `users` table; no migration and Flyway remains disabled

**Testing**: JUnit 5, Spring MockMvc, AssertJ, Testcontainers PostgreSQL, JaCoCo, Checkstyle

**Target Platform**: Linux-compatible JVM web service

**Project Type**: Single Spring Boot web service

**Performance Goals**: 1 second p95 for an owner-scoped profile update

**Constraints**: Exact owner isolation, established normalization and response parity, no sensitive diagnostics, no frontend/routing/deployment/CI changes

**Scale/Scope**: One PATCH operation, one command port/use case/adapter, request/result models, security integration, and focused tests

## Constitution Check

*GATE: Passed before research and after design.*

- Maintainability: one cohesive command boundary reuses the existing identity context and dependencies.
- Testing: unit, contract, database, security, failure, metrics, and performance coverage are required.
- Experience: existing validation, normalization, response fields, and Chinese not-found behavior remain stable.
- Performance: the one-second p95 write budget is measured with representative accounts.
- Operations: no-store responses and bounded metrics exclude every profile value and identifier.
- Scope: avatar upload, credentials, email, sessions, frontend, deployment, routing, and CI/CD stay unchanged.

## Project Structure

```text
specs/013-profile-update/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
└── tasks.md

src/main/java/com/jobtrace/identityaccess/
├── application/
├── domain/
├── infrastructure/
└── web/

src/test/java/com/jobtrace/identityaccess/
docs/migration-slices/
```

**Structure Decision**: Extend the existing identity-access bounded context and preserve web-to-application-to-domain-port-to-infrastructure dependencies.

## Complexity Tracking

No constitution violations or exceptions are required.
