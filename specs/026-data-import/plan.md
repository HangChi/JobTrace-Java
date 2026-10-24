# Implementation Plan: Application Data Import

**Branch**: `codex/026-data-import` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/026-data-import/spec.md`

## Summary

Add the two existing private import routes to the Java modular monolith. Parse bounded CSV/XLSX input, normalize and validate rows, store owner-scoped preview batches with set-based duplicate lookup, and call the existing PostgreSQL confirmation function for once-only per-row results. Existing exports are unchanged.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Spring Web/Security/JDBC, Apache POI 5.5.1, Jackson

**Storage**: Existing PostgreSQL application and import tables/functions; no new migrations

**Testing**: JUnit 5, Spring MockMvc, Testcontainers PostgreSQL, JaCoCo and `./mvnw verify`

**Target Platform**: Existing Spring Boot server

**Project Type**: Modular monolith web service

**Performance Goals**: Representative preview under 500 ms p95 and confirmation under 1 s p95 in documented local conditions; measure large batch separately

**Constraints**: 5 MiB file, 10,000 rows, 100 columns, 200,000 cells; owner isolation; no formula evaluation; 80% changed-code line/branch coverage

**Scale/Scope**: Two import HTTP routes and existing export compatibility; no frontend, CI/CD or production cutover

## Constitution Check

- **Maintainability**: Domain parsing and validation are separated from web handling; infrastructure owns SQL and file parsing. No new deployment unit or dependency.
- **Testing**: Parser unit tests, MockMvc contracts, real PostgreSQL integration and export/import round trip. Changed-code line and branch gates remain at least 80%.
- **UX/accessibility**: Existing UI is unchanged; preserve row-level messages and predictable preview/confirm response shapes for its current states.
- **Performance**: Set-based duplicate lookup, bounded input, and representative latency samples; 10,000-row behavior measured separately.
- **Security/operations**: Owner derives from verified identity, output is no-store, and storage failures do not reveal private data. Old service remains production writer until cutover.

The design passes the constitution gate before and after research. No exception is requested.

## Project Structure

### Documentation

```text
specs/026-data-import/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/openapi.yaml
├── quickstart.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code

```text
src/main/java/com/jobtrace/datatransfer/
├── domain/             # normalized import rows and validation
├── application/        # preview and confirm use cases plus store ports
├── infrastructure/     # bounded CSV/XLSX reader and PostgreSQL store
└── web/                # private multipart preview and JSON confirm routes
src/test/java/com/jobtrace/datatransfer/
src/test/resources/postgres/
docs/migration-slices/
```

**Structure Decision**: Extend the existing data-transfer module. Web depends on application/domain; infrastructure implements application ports. Reuse the shared application catalog and existing database function.

## Design Sequence

1. Add a strict bounded file reader and mapping/row validator. Preserve current alias/default behavior and optional stage history.
2. Build a preview use case and JDBC store that detects only owner-matching duplicates in one query, then persists batch and rows in one transaction. Expire a replaced owned batch only after the replacement is durable.
3. Build a confirmation use case calling the existing owner-aware PostgreSQL function; map missing, expired and processed conditions to safe HTTP responses.
4. Verify contracts, owner isolation, concurrency, partial outcomes and XLSX export-to-import compatibility. Record timing and coverage evidence.

## Rollback Boundary

No production route or writer ownership changes. Reverting this branch removes the Java import route while the old service and existing database remain authoritative. Stored preview rows can expire normally.
