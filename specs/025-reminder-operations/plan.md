# Implementation Plan: Reminder Operations and Delivery

**Branch**: `codex/025-reminder-operations` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Extend Java reminder reads with owner-scoped commands, settings writes, retry and a secret-protected internal delivery job. Preserve PostgreSQL tables and the current mail webhook; the old service keeps production route and scheduler ownership until a separate cutover.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1
**Primary Dependencies**: Spring Web, Security, JDBC, Jackson, existing HttpClient; no new dependency
**Storage**: Existing PostgreSQL reminders, attempts, applications, stages, users and suggestion resolutions
**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, fake mail server, JaCoCo
**Target Platform**: Existing Spring Boot modular monolith
**Project Type**: JSON web service
**Performance Goals**: reads <= 500 ms p95, interactive writes <= 1 s p95 under documented local load; bounded delivery batch
**Constraints**: No schema, frontend, CI/CD, deployment or production routing change; no real mail in tests
**Scale/Scope**: User reminder commands, preference PATCH and internal delivery route

## Constitution Check

- Maintainability: web validation, application orchestration and JDBC adapters remain separate; no new service or dependency.
- Testing: unit, real PostgreSQL integration, HTTP and fake-mail tests; changed code >= 80% line and branch coverage.
- UX/accessibility: no browser changes; preserve existing JSON, wording, validation and feedback states.
- Performance: bounded reads/batches, claim lease and attempts; local p95 evidence.
- Observability: request ID and safe outcome/latency signals; no email, secret, title or recipient in logs.
- Rollback: old service remains production route owner and active scheduler, with unchanged shared schema.

## Project Structure

```text
src/main/java/com/jobtrace/reminders/{domain,application,infrastructure,web}/
src/test/java/com/jobtrace/reminders/
src/test/resources/postgres/
specs/025-reminder-operations/
```

## Sequencing

Implement private CRUD and lifecycle, then preferences and retry, then internal claim/send/finalize. Validate boundaries with real PostgreSQL and fake mail; document routing and rollback. All work is performed without subagents.
