# Implementation Plan: Java Migration Code Hardening

**Branch**: `codex/029-code-hardening` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

## Summary

Audit legacy routes against Java route groups, document supported and legacy-only behavior, add focused security/concurrency/error and cross-workflow regressions, and keep the shared-schema boundary enforceable. Do not revisit 028 coverage or edit CI/CD.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4, PostgreSQL 17; TypeScript frontend is unchanged.

**Primary Dependencies**: Existing Spring MVC/Security/JDBC, JUnit, MockMvc, Testcontainers.

**Storage**: Existing externally owned PostgreSQL schema; no production migration.

**Testing**: Maven verify, local PostgreSQL fixtures, deterministic controller and concurrency tests.

**Target Platform**: Existing Java modular monolith and shared legacy database.

**Project Type**: Web service with embedded browser assets.

**Performance Goals**: Existing read <=500 ms p95 and write <=1 s p95 budgets.

**Constraints**: No crawler, CI/CD, production route switch, Flyway activation, or stage-028 coverage work.

**Scale/Scope**: Existing user/admin APIs and shared error/security boundaries, not a new feature family.

## Constitution Check

- Cohesive implementation: centralize only repeated error behavior demonstrated by the audit; avoid new services or dependencies.
- Testing: add failing regression tests before fixes, run full Maven verification, retain the existing coverage floor for changed Java code.
- UX: preserve existing response codes and safe error messages used by clients; no frontend redesign.
- Performance: reuse existing budgets and avoid new database round trips on reads.

The design adds no architectural layer or exception. Rechecked after contract design: the legacy service remains schema/ingestion owner and Java hardening is limited to existing web/security boundaries.

## Project Structure

```text
specs/029-code-hardening/       # scope, decisions, contract matrix, validation
src/main/java/com/jobtrace/    # existing modular-monolith web/security boundaries
src/test/java/com/jobtrace/    # focused regression and cross-workflow tests
docs/database-baseline.md      # existing activation gate, clarified if needed
```

**Structure Decision**: Reuse existing modules and tests; do not introduce a separate service, frontend package or migration directory.

## Delivery Sequence

1. Inventory legacy-to-Java routes and explicit legacy-only crawl/scheduler dispositions.
2. Add security and error-contract regressions, then make minimal fixes.
3. Add concurrency and cross-workflow regression around shared database state.
4. Verify schema non-ownership and full Maven suite; record remaining manual checks for the user.
