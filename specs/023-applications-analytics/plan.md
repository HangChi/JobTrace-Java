# Implementation Plan: Applications and Analytics Operations

**Branch**: `codex/023-applications-analytics` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

## Summary

Extend the Java modular monolith's existing read slices with owner-scoped application commands, stage commands, analytics report query, and progress-reminder resolution. Reuse the legacy schema/functions and the Java authentication boundary; keep production routing unchanged.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1
**Dependencies**: Spring Web/Security/JDBC, existing Jackson and PostgreSQL drivers
**Storage**: Legacy PostgreSQL applications, stage occurrences, events, interview reviews, and progress reminder completions
**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo
**Performance Goals**: report reads <= 500 ms p95, interactive writes <= 1 s p95
**Constraints**: no schema ownership, no frontend or CI/CD changes, preserve exact legacy API behavior

## Constitution Check

- Maintainability: web validates requests; application services own use cases; JDBC adapters alone know SQL and SQLSTATE translation.
- Testing: independent contract, rules, HTTP, and PostgreSQL tests cover 80% of changed lines and branches.
- UX: no frontend changes; preserve existing Chinese validation messages, status codes, empty results, and no-store semantics.
- Performance: use database aggregation, bounded bulk deletion, indexed owner predicates, and representative p95 checks.
- Rollback: old service remains route owner; Java endpoints can be withdrawn without schema reversal.

## Project Structure

```text
src/main/java/com/jobtrace/applications/{domain,application,infrastructure,web}/
src/main/java/com/jobtrace/analytics/{domain,application,infrastructure,web}/
src/test/java/com/jobtrace/{applications,analytics}/
specs/023-applications-analytics/
```

## Sequencing

First add application commands and contract tests, then stage commands. Implement report range/rules separately from SQL aggregates, followed by progress resolution. Verify the full Maven suite and document route ownership. No new dependency, service, migration, frontend, or CI/CD change is justified.
