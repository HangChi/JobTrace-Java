# Tasks: Applications and Analytics Operations

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)

## Setup and foundation

- [x] T001 Record legacy routes, write functions, report semantics and owner boundary in `research.md` and `contracts/openapi.yaml`.
- [x] T002 Define aggregate ownership and rollback in `data-model.md` and `quickstart.md`.

## User Story 1 — Application commands (P1)

- [x] T003 Add validation and HTTP contract tests for create, edit, status change, single and bulk deletion.
- [x] T004 Implement request models, validation and owner-scoped application command use cases.
- [x] T005 Implement JDBC commands using existing owner-aware functions and SQLSTATE mapping.
- [x] T006 Expose exact application routes and cover conflict, foreign-owner and storage failures.

## User Story 2 — Stage commands (P2)

- [x] T007 Add stage HTTP, rule and database tests for duplicate, chronology, terminal, conflict and isolation cases.
- [x] T008 Implement add, edit and remove stage use cases and PostgreSQL adapters.
- [x] T009 Expose exact stage routes and returned application detail.

## User Story 3 — Report and progress reminders (P3)

- [x] T010 Add report range, formula, HTTP and aggregate database tests.
- [x] T011 Implement report range parsing and result model/rules.
- [x] T012 Implement owner-scoped PostgreSQL report aggregates and `/api/analytics/report`.
- [x] T013 Add progress-resolution HTTP and PostgreSQL isolation/idempotency tests.
- [x] T014 Implement completion and resolution routes using existing storage.

## Verification

- [x] T015 Run focused tests and full `./mvnw verify`; record coverage and local p95 evidence in `quickstart.md`.
- [x] T016 Document route ownership and rollback without changing production routing.

## Dependencies

T003–T006 establish application mutation; T007–T009 build on its owner/version conventions. T010–T012 are independently testable report work. T013–T014 use existing stage IDs. T015–T016 close the stage. No subagents or CI/CD work.
