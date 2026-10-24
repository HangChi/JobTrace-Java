# Implementation Plan: Account Session Read Model

**Branch**: `codex/011-session-read-model` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-session-read-model/spec.md`

## Summary

Add the owner-scoped `GET /api/profile/sessions` compatibility path. Reuse the signed identity bridge for account ownership, accept the current Better Auth session identifier as bounded trusted caller metadata, query only unexpired owned rows through a parameterized JDBC adapter, and return the established JSON shape without taking session mutation or authentication ownership.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Security, Spring JDBC, Micrometer

**Storage**: Existing PostgreSQL 17 `sessions` table; no migrations and Flyway remains disabled

**Testing**: JUnit 5, Spring MockMvc, AssertJ, Testcontainers PostgreSQL, ArchUnit, JaCoCo, Checkstyle

**Target Platform**: Linux-compatible JVM web service

**Project Type**: Single Spring Boot web service

**Performance Goals**: 500 ms p95 for 500 active representative sessions

**Constraints**: Owner isolation, exact active-session contract, one parameterized read, no sensitive logs, no writes, no frontend/routing/deployment/CI changes

**Scale/Scope**: One GET route, one read model, one use case, one repository adapter, and supporting contract/security/performance tests

## Constitution Check

*GATE: Passed before research and after design.*

- Maintainability: one narrow domain record, port, use case, adapter, controller, and metrics component; no new dependency.
- Testing: unit, contract, storage, authorization, isolation, failure, read-only, and performance coverage are planned; changed code remains under repository JaCoCo gates.
- Experience: response fields and null semantics match the current client; no UI is changed.
- Performance: one bounded set-based query is measured against the 500 ms p95 read budget.
- Operations: cache prevention and bounded non-sensitive telemetry are explicit; rollback remains route ownership in the legacy service.
- Scope: no schema, session write, frontend, deployment, routing, or CI/CD ownership moves.

## Project Structure

### Documentation (this feature)

```text
specs/011-session-read-model/
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
src/main/java/com/jobtrace/identityaccess/
├── application/
├── domain/
├── infrastructure/
└── web/

src/test/java/com/jobtrace/identityaccess/
src/test/resources/contracts/identityaccess/
docs/migration-slices/
```

**Structure Decision**: Extend the existing identity-access bounded context and preserve the established web-to-application-to-domain/port-to-infrastructure dependency direction.

## Complexity Tracking

No constitution violations or exceptions are required.
