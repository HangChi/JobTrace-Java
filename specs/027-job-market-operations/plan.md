# Implementation Plan: Job Market Operations

**Branch**: `codex/027-job-market` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

## Summary

Complete the Java marketplace read model with owner-scoped favorites and administrator inspection/curation of existing shared PostgreSQL data. The legacy service remains the only crawler and synchronization worker. Java must not ship a second default catalog, external fetcher or lease claimant.

## Technical Context

**Language/Version**: Java 25, Spring Boot 4 and existing Maven modules.

**Primary Dependencies**: Spring Web, Spring JDBC, Jackson.

**Storage**: Existing PostgreSQL marketplace tables and routines; no new production migration in this stage.

**Testing**: JUnit, MockMvc, Testcontainers PostgreSQL and JaCoCo; assert read paths do not mutate ingestion tables.

**Target Platform**: Java web service deployed alongside the legacy route owner.

**Project Type**: Modular monolith web service.

**Performance Goals**: Local representative reads <=500 ms p95 and user-initiated writes <=1 s p95; separately measure external collection.

**Constraints**: Preserve owner/admin authorization, validate manually submitted source URLs, and leave ingestion leases untouched. Changed production code >=80% line and branch coverage.

**Scale/Scope**: Existing marketplace tables, administrator catalog and candidate workflows, source health and run history. No Java ATS/WeChat intake.

## Constitution Check

- Maintainability: keep `web -> application -> domain` and infrastructure behind ports; no new deployment unit.
- Testing: unit rules, HTTP/security, database and fake-network integration tests for each behavior change; run `./mvnw verify`.
- Consistent UX: preserve existing response fields and error semantics; frontend is unchanged.
- Performance: measure representative database reads and user-initiated curation writes.
- Observability: display persisted run metrics and sanitized failures without raw secrets or article contents.

No planned constitution exception. Rechecked after design: the existing schema and libraries support this plan; no architecture change is needed.

## Project Structure

```text
specs/027-job-market-operations/
  spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
src/main/java/com/jobtrace/jobmarket/
  domain/ application/ infrastructure/ web/
src/test/java/com/jobtrace/jobmarket/
src/test/resources/postgres/
docs/migration-slices/
```

**Structure Decision**: Extend the current jobmarket module and its tests. Database storage stays behind application ports; no Java network adapter is needed.

## Delivery Sequence

1. Owner-scoped favorite mutation against the current read model.
2. Administrator source/catalog and candidate review operations.
3. Read persisted candidate/source health and sync history; remove unused Java crawler/lease scaffolding.
4. Contract, security, concurrency, performance and coverage validation.

Each sequence has an independently testable slice. Ingestion route ownership and scheduled invocation remain with the legacy system. Java receives already persisted marketplace data through its configured database connection.
