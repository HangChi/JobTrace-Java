# Implementation Plan: Account Session Revocation

**Branch**: `codex/012-session-revocation` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

## Summary

Add `DELETE /api/profile/sessions` beside the feature 011 read path. Validate the opaque request identifier, derive ownership only from the signed bridge principal, perform one owner-scoped `DELETE ... RETURNING`, preserve the established success/not-found contract, and add bounded write metrics without moving broader session authority.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Security, Spring JDBC, Micrometer

**Storage**: Existing PostgreSQL 17 `sessions` table; no migration and Flyway remains disabled

**Testing**: JUnit 5, Spring MockMvc, AssertJ, Testcontainers PostgreSQL, JaCoCo, Checkstyle

**Target Platform**: Linux-compatible JVM web service

**Project Type**: Single Spring Boot web service

**Performance Goals**: 1 second p95 for an owner-scoped revocation

**Constraints**: Exact owner isolation, case-sensitive opaque IDs, atomic single-row deletion, safe not-found behavior, no sensitive logs, no frontend/routing/deployment/CI changes

**Scale/Scope**: One DELETE operation, one command port/use case/adapter, request/result models, controller integration, and focused tests

## Constitution Check

*GATE: Passed before research and after design.*

- Maintainability: extend the existing session context with one command boundary and no new dependency.
- Testing: unit, contract, database, concurrency, security, failure, metrics, and performance tests cover the mutation.
- Experience: preserve the current response and error meaning consumed by the existing profile UI.
- Performance: measure the one-second p95 write budget with an indexed owner-and-ID deletion.
- Operations: existing no-store filtering applies; metrics contain only bounded outcome and latency labels.
- Scope: no authentication, schema, frontend, deployment, routing, or CI/CD ownership changes.

## Project Structure

```text
specs/012-session-revocation/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
└── tasks.md

src/main/java/com/jobtrace/identityaccess/
├── application/
├── domain/
├── infrastructure/
└── web/

src/test/java/com/jobtrace/identityaccess/
docs/migration-slices/
```

**Structure Decision**: Extend feature 011 in the existing identity-access bounded context and preserve web-to-application-to-domain-port-to-infrastructure dependencies.

## Complexity Tracking

No constitution violations or exceptions are required.
