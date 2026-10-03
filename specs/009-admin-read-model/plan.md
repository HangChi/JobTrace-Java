# Implementation Plan: Administration Read Model

**Branch**: `codex/009-admin-read-model` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-admin-read-model/spec.md`

## Summary

Add administrator-authorized, read-only compatibility endpoints for operational summary, managed users, user detail, and access audit history. Reuse the signed bridge, query unchanged PostgreSQL tables with parameterized Spring JDBC, preserve partial summary availability and exact legacy paging/filter behavior, and expose no mutation or deployment work.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC/Security/JDBC, Jackson, Micrometer

**Storage**: Existing PostgreSQL 17 users, sessions, applications, interviews, stages/questions/actions, and append-only admin audit tables; read-only; Flyway disabled

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL 17, ArchUnit, JaCoCo, Checkstyle

**Target Platform**: Linux JVM packaged as the existing single Spring Boot artifact

**Project Type**: Modular-monolith web service

**Performance Goals**: All reads <=500 ms p95 on 1,000 users with fixed query counts

**Constraints**: Exact admin authorization; opaque text user IDs; Asia/Shanghai dates; private no-store; no writes, migrations, frontend, CI/CD, deployment, or routing changes

**Scale/Scope**: Four protected GET route shapes; list limits 1..100; user-content pages fixed at 10; 30 trend points; 10 recent audits

## Constitution Check

- **Maintainability**: Pass. One `admin` read context follows `web -> application -> domain`; infrastructure implements one read port.
- **Testing**: Pass. Behavior, storage, contract, security, read-only, architecture, coverage, and performance checks are planned.
- **UX**: Pass. Existing browser routes, copy, loading/empty/error behavior, keyboard behavior, and responsive UI are unchanged.
- **Performance**: Pass. The default 500 ms p95 read budget is explicit and measured with reproducible data.
- **Post-design recheck**: Pass. No new dependency, deployment unit, production schema, or write owner is introduced.

## Project Structure

```text
src/main/java/com/jobtrace/admin/
├── domain/
├── application/
├── infrastructure/
└── web/

src/test/java/com/jobtrace/admin/
src/test/resources/postgres/admin-read-model.sql
src/test/resources/contracts/admin/
specs/009-admin-read-model/
```

**Structure Decision**: Add a cohesive administration read context to the existing modular monolith and reuse shared identity, problem, request-ID, database, and metrics infrastructure. No frontend file changes.

## Design Sequence

1. Capture legacy JSON and SQL behavior with synthetic data.
2. Add strict query/domain types, admin-principal enforcement, read port, and exact bridge path allowlist.
3. Implement summary as two independent reads.
4. Implement user directory and bounded detail composition.
5. Implement audit listing and snapshot mapping.
6. Add safe errors/no-store, bounded metrics, architecture/read-only checks, representative performance, and full local verification.
