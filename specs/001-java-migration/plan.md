# Implementation Plan: Java Migration Foundation

**Branch**: `main` | **Date**: 2026-09-30 | **Completed**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-java-migration/spec.md`

## Summary

Establish a new repository that can incrementally replace the existing Next.js backend while retaining a React and TypeScript browser application. Spring Boot owns the future API and runtime, Vite produces static browser assets, and Maven packages both into one deployable artifact. The existing PostgreSQL schema remains authoritative; Flyway execution is disabled until a reviewed baseline is defined. Migration proceeds by contract-tested slices with one writer per aggregate and authentication migrated last.

## Technical Context

**Language/Version**: Java 21 LTS; TypeScript 6; Node.js 24+ for development and CI only

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Security, Spring JDBC, jOOQ, Flyway, React 19, Vite 8

**Storage**: Existing PostgreSQL 17 schema and stored routines; no schema changes in the foundation slice

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers, Vitest, Testing Library; existing Playwright suite remains in the source repository during migration

**Target Platform**: Linux production host or OCI container with a Java 21 runtime; modern desktop and mobile browsers

**Project Type**: Single repository web application with a Spring Boot backend and a separately built React frontend embedded into the production JAR

**Performance Goals**: Preserve existing default budgets of reads at or below 500 ms p95, writes at or below 1 s p95, LCP at or below 2.5 s p75, INP at or below 200 ms p75, and CLS at or below 0.1

**Constraints**: Production must not require Node.js; no legacy schema replay before baseline approval; credentials remain external; only one service writes an aggregate during migration; public identity headers are untrusted

**Scale/Scope**: Foundation for the existing seven domain modules, approximately 65 HTTP handlers, 66 legacy migrations, and the current multi-user PostgreSQL data set

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Design response | Pre-design | Post-design |
|---|---|---:|---:|
| I. Maintainable Code by Design | Preserve domain module names, keep one deployable backend, avoid microservices and premature ORM mapping | PASS | PASS |
| II. Testing Is a Release Gate | Add backend/frontend tests and CI; require contract comparison for every migrated slice | PASS | PASS |
| III. Consistent and Accessible UX | Retain React/TypeScript and existing UX contracts; foundation shell uses semantic structure and visible status | PASS | PASS |
| IV. Measured Performance Budgets | Carry existing API and Core Web Vitals budgets into migration acceptance | PASS | PASS |
| Documentation and contracts | Include specification, plan, research, data model, OpenAPI health contract, quickstart, and tasks | PASS | PASS |
| Security and secrets | Example-only configuration, disabled legacy migrations, authenticated future endpoints, no trusted public identity headers | PASS | PASS |
| Rollback | Route each slice independently and keep the existing implementation available until acceptance passes | PASS | PASS |

No constitution exception is required. The frontend toolchain is a build-time dependency justified by preserving the existing accessible React experience while removing Node.js from production.

Delivery uses Constitution v1.1.0 Solo-Maintainer Mode while the repository has exactly one human
maintainer with write access. Pull requests, written self-review, CI, security checks, resolved
conversations, linear history, and protected history remain mandatory. Independent approval becomes
mandatory again before the first production merge after a second human receives write access.

## Phase 0 Decisions

- Use Java 21 LTS because it is installed locally, broadly supported, and sufficient for the selected Spring line.
- Use the current stable Spring Boot line selected from official Initializr metadata.
- Use Maven Wrapper so contributors and CI do not depend on a globally installed Maven.
- Prefer jOOQ and Spring JDBC over JPA because the existing system relies on PostgreSQL functions, JSONB, custom enums, reporting queries, leases, and explicit transaction behavior.
- Build the frontend independently with Vite, then include `frontend/dist` in the executable JAR during `package`.
- Keep Flyway disabled by default until legacy migration checksums and the baseline version are documented and verified.
- Keep Better Auth as the identity owner during early slices; specify and test a signed server-to-server identity bridge before protected API migration.

Full rationale and rejected alternatives are recorded in [research.md](research.md).

## Phase 1 Design

The foundation introduces build and migration-control entities rather than new user data. Their fields and state transitions are documented in [data-model.md](data-model.md). The implemented interface includes liveness, readiness, and the verified read-only analytics pilot documented in [contracts/openapi.yaml](contracts/openapi.yaml). [quickstart.md](quickstart.md) provides the end-to-end verification path.

Each later migration slice must:

1. Capture the legacy endpoint and persistence behavior as a contract baseline.
2. Implement the bounded Java module using existing database routines where practical.
3. Run identical acceptance fixtures against both implementations.
4. Assign exactly one writer before routing production traffic.
5. Record health signals and a rollback route.
6. Remove the legacy implementation only after an observation window.

## Project Structure

### Documentation (this feature)

```text
specs/001-java-migration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/jobtrace/
│   │   ├── JobTraceApplication.java
│   │   ├── shared/
│   │   ├── applications/
│   │   ├── interviews/
│   │   ├── analytics/
│   │   ├── reminders/
│   │   ├── datatransfer/
│   │   ├── jobmarket/
│   │   └── identityaccess/
│   └── resources/
└── test/java/com/jobtrace/

frontend/
├── src/
├── public/
├── package.json
└── vite.config.ts

docs/
├── architecture.md
├── migration.md
├── repository-setup.md
└── adr/

.github/workflows/ci.yml
```

**Structure Decision**: Use a single Maven project at the repository root and a `frontend/` Vite project. Backend code is organized as a modular monolith matching the existing domain vocabulary. The production JAR embeds the compiled frontend, while development runs the Vite and Spring processes independently.

## Complexity Tracking

No constitution violations require justification.
